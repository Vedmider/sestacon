package io.github.vedmider.sestacon.adapter.out.store;

import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JdbcTemplate-backed {@link DocumentVersionStore} over {@code rag_documents}/{@code rag_document_chunks}.
 * Independent of the retrieval backend, so version tracking works in both vector and hybrid modes.
 */
public class PgDocumentVersionStore implements DocumentVersionStore {

    private final JdbcTemplate jdbc;

    public PgDocumentVersionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DocumentDiff diff(String documentId, Map<String, String> incomingSectionHashes) {
        return DocumentDiff.compare(documentId, storedSectionHashes(documentId), incomingSectionHashes);
    }

    @Override
    @Transactional
    public void record(String documentId, String docHash, long sizeBytes,
                       Map<String, String> sectionHashes) {
        jdbc.update("""
                INSERT INTO rag_documents (document_id, doc_hash, size_bytes, chunk_count, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (document_id) DO UPDATE
                   SET doc_hash = EXCLUDED.doc_hash, size_bytes = EXCLUDED.size_bytes,
                       chunk_count = EXCLUDED.chunk_count, updated_at = now()
                """, documentId, docHash, sizeBytes, sectionHashes.size());
        jdbc.update("DELETE FROM rag_document_chunks WHERE document_id = ?", documentId);
        List<Map.Entry<String, String>> entries = new ArrayList<>(sectionHashes.entrySet());
        jdbc.batchUpdate(
                "INSERT INTO rag_document_chunks (document_id, section_path, content_hash, ord) "
                        + "VALUES (?, ?, ?, ?)",
                entries, entries.size(),
                (ps, e) -> {
                    ps.setString(1, documentId);
                    ps.setString(2, e.getKey());
                    ps.setString(3, e.getValue());
                    ps.setInt(4, entries.indexOf(e));
                });
    }

    @Override
    public Optional<DocumentVersion> find(String documentId) {
        List<DocumentVersion> rows = jdbc.query(
                "SELECT doc_hash, size_bytes, chunk_count FROM rag_documents WHERE document_id = ?",
                (rs, n) -> new DocumentVersion(documentId, rs.getString("doc_hash"),
                        rs.getLong("size_bytes"), rs.getInt("chunk_count"),
                        storedSectionHashes(documentId)),
                documentId);
        return rows.stream().findFirst();
    }

    @Override
    public int delete(String documentId) {
        return jdbc.update("DELETE FROM rag_documents WHERE document_id = ?", documentId);
    }

    private Map<String, String> storedSectionHashes(String documentId) {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query("SELECT section_path, content_hash FROM rag_document_chunks "
                        + "WHERE document_id = ? ORDER BY ord",
                rs -> { out.put(rs.getString("section_path"), rs.getString("content_hash")); },
                documentId);
        return out;
    }
}
