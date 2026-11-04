package com.example.rag.adapter.out.store;

import com.example.rag.domain.model.DocumentChunk;
import com.example.rag.domain.model.RetrievedChunk;
import com.example.rag.domain.port.out.DocumentStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link DocumentStore} backed by Spring AI's pgvector {@link VectorStore}. Dense-vector
 * retrieval with adapter-side RBAC: results are filtered by their {@code aclTags} metadata
 * against the caller's roles (public/untagged chunks are visible to everyone). For
 * full-text + RBAC + RRF in one SQL round-trip, select {@code app.retrieval.mode=hybrid}.
 */
public class PgVectorDocumentStore implements DocumentStore {

    private final VectorStore vectorStore;

    public PgVectorDocumentStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void add(List<DocumentChunk> chunks) {
        List<Document> documents = chunks.stream()
                .map(PgVectorDocumentStore::toDocument)
                .toList();
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    private static Document toDocument(DocumentChunk c) {
        Map<String, Object> metadata = new HashMap<>(c.metadata());
        metadata.put("source", c.documentId());
        metadata.put("documentId", c.documentId());
        metadata.put("sectionPath", c.sectionPath());
        metadata.put("aclTags", String.join(",", c.aclTags()));
        return new Document(c.content(), metadata);
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK) {
        return search(query, topK, List.of());
    }

    @Override
    public List<RetrievedChunk> search(String query, int topK, List<String> roles) {
        int pool = Math.max(topK * 4, 20);
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(pool).build());
        return hits.stream()
                .filter(d -> visibleTo(d, roles))
                .limit(topK)
                .map(PgVectorDocumentStore::toChunk)
                .toList();
    }

    /** RBAC at the adapter: a chunk is visible if it is public, untagged, or shares a role. */
    private static boolean visibleTo(Document d, List<String> roles) {
        Object raw = d.getMetadata().get("aclTags");
        if (raw == null || raw.toString().isBlank()) {
            return true;
        }
        Set<String> tags = new HashSet<>(Arrays.asList(raw.toString().split(",")));
        if (tags.contains(DocumentChunk.PUBLIC)) {
            return true;
        }
        return roles != null && roles.stream().anyMatch(tags::contains);
    }

    private static RetrievedChunk toChunk(Document d) {
        Object source = d.getMetadata().getOrDefault("source", "unknown");
        double score = d.getScore() != null ? d.getScore() : 0.0;
        return new RetrievedChunk(d.getText(), String.valueOf(source), score);
    }
}
