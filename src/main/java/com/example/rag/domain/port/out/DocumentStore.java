package com.example.rag.domain.port.out;

import com.example.rag.domain.model.DocumentChunk;
import com.example.rag.domain.model.RetrievedChunk;

import java.util.List;

/** Outbound port for persisting and similarity-searching document chunks. */
public interface DocumentStore {

    void add(List<DocumentChunk> chunks);

    List<RetrievedChunk> search(String query, int topK);

    /**
     * Retrieves with role-based access control: only chunks whose {@code aclTags} are
     * {@code "public"} or intersect {@code roles} may be returned. Stores that cannot
     * enforce RBAC ignore {@code roles} and fall back to the plain search.
     */
    default List<RetrievedChunk> search(String query, int topK, List<String> roles) {
        return search(query, topK);
    }
}
