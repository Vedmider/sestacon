package io.github.vedmider.sestacon.adapter.out.store;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.EmbeddingProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hybrid {@link DocumentStore} that fuses, in a single SQL round-trip:
 * <ul>
 *   <li>dense-vector similarity (pgvector {@code <=>} cosine distance),</li>
 *   <li>exact lexical full-text search (PostgreSQL {@code tsvector}/{@code tsquery}),</li>
 *   <li>RBAC filtering ({@code allowed_roles && caller_roles}) applied before ranking, and</li>
 *   <li>Reciprocal Rank Fusion of the two ranked lists.</li>
 * </ul>
 * It reads/writes the {@code rag_chunks} table (created by Flyway migration {@code V2})
 * and embeds content/queries via {@link EmbeddingProvider}.
 */
public class HybridPgDocumentStore implements DocumentStore {

    private static final Pattern SAFE_IDENT = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    private final JdbcTemplate jdbc;
    private final EmbeddingProvider embeddings;
    private final ObjectMapper objectMapper;
    private final int rrfK;
    private final String tsConfig;

    public HybridPgDocumentStore(JdbcTemplate jdbc, EmbeddingProvider embeddings,
                                 ObjectMapper objectMapper, int rrfK, String textSearchConfig) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.objectMapper = objectMapper;
        this.rrfK = rrfK;
        this.tsConfig = SAFE_IDENT.matcher(textSearchConfig).matches() ? textSearchConfig : "english";
    }

    @Override
    public void add(List<DocumentChunk> chunks) {
        for (DocumentChunk c : chunks) {
            jdbc.update("""
                    INSERT INTO rag_chunks (document_id, section_path, content, metadata,
                                            allowed_roles, embedding)
                    VALUES (?, ?, ?, ?::jsonb, ?::text[], ?::vector)""",
                    c.documentId(), c.sectionPath(), c.content(), toJson(c.metadata()),
                    toPgArray(c.aclTags()), toVector(embeddings.embed(c.content())));
        }
    }

    @Override
    public int deleteByDocumentId(String documentId, List<String> roles) {
        String callerRoles = toPgArray(withPublic(roles));
        Integer hidden = jdbc.queryForObject("""
                SELECT count(*) FROM rag_chunks
                WHERE document_id = ? AND NOT (allowed_roles && ?::text[])""",
                Integer.class, documentId, callerRoles);
        if (hidden != null && hidden > 0) {
            throw new DeleteNotPermittedException(documentId);
        }
        return jdbc.update("DELETE FROM rag_chunks WHERE document_id = ?", documentId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void replace(List<DocumentChunk> chunks, List<String> roles) {
        chunks.stream().map(DocumentChunk::documentId).distinct()
                .forEach(id -> deleteByDocumentId(id, roles));
        add(chunks);
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        return search(query, topK, List.of());
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK, List<String> roles) {
        return search(query, topK, roles, java.util.Map.of());
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK, List<String> roles,
                                       java.util.Map<String, String> metadataFilter) {
        String queryVector = toVector(embeddings.embed(query));
        String callerRoles = toPgArray(withPublic(roles));
        int candidates = Math.max(topK * 5, 20);
        boolean filtered = metadataFilter != null && !metadataFilter.isEmpty();
        String metaClause = filtered ? " AND metadata @> ?::jsonb" : "";
        String metaJson = filtered ? metaToJson(metadataFilter) : null;

        String sql = """
                WITH vec AS (
                    SELECT id, document_id, content,
                           ROW_NUMBER() OVER (ORDER BY embedding <=> ?::vector) AS rnk
                    FROM rag_chunks
                    WHERE allowed_roles && ?::text[]%1$s
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                ),
                txt AS (
                    SELECT id, document_id, content,
                           ROW_NUMBER() OVER (ORDER BY ts_rank(content_tsv, q.query) DESC) AS rnk
                    FROM rag_chunks, websearch_to_tsquery('%2$s', ?) AS q(query)
                    WHERE allowed_roles && ?::text[] AND content_tsv @@ q.query%1$s
                    ORDER BY ts_rank(content_tsv, q.query) DESC
                    LIMIT ?
                )
                SELECT COALESCE(v.document_id, t.document_id) AS document_id,
                       COALESCE(v.content, t.content) AS content,
                       COALESCE(1.0 / (? + v.rnk), 0) + COALESCE(1.0 / (? + t.rnk), 0) AS score
                FROM vec v FULL OUTER JOIN txt t ON v.id = t.id
                ORDER BY score DESC
                LIMIT ?
                """.formatted(metaClause, tsConfig);

        List<Object> args = new ArrayList<>();
        args.add(queryVector);
        args.add(callerRoles);
        if (filtered) {
            args.add(metaJson);
        }
        args.add(queryVector);
        args.add(candidates);
        args.add(query);
        args.add(callerRoles);
        if (filtered) {
            args.add(metaJson);
        }
        args.add(candidates);
        args.add(rrfK);
        args.add(rrfK);
        args.add(topK);

        return jdbc.query(sql,
                (rs, i) -> new RetrievedChunk(rs.getString("content"),
                        rs.getString("document_id"), rs.getDouble("score")),
                args.toArray());
    }

    private String metaToJson(java.util.Map<String, String> filter) {
        try {
            return objectMapper.writeValueAsString(filter);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static List<String> withPublic(List<String> roles) {
        Set<String> all = new LinkedHashSet<>();
        all.add(DocumentChunk.PUBLIC);
        if (roles != null) {
            all.addAll(roles);
        }
        return new ArrayList<>(all);
    }

    private String toJson(java.util.Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        }
        catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String toPgArray(List<String> values) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String escaped = values.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append('"').append(escaped).append('"');
        }
        return sb.append('}').toString();
    }

    private static String toVector(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        return sb.append(']').toString();
    }
}
