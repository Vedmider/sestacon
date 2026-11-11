package io.github.vedmider.sestacon.domain.port.in;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;

import java.util.List;

/**
 * Inbound port: retrieval-only. Returns the top chunks for a query with no LLM call —
 * the caller (e.g. an agent) supplies its own reasoning. This is the token-cheap path;
 * contrast {@link RagQueryUseCase}, which also invokes the chat model.
 */
public interface RetrieveUseCase {

    /** Retrieves with no caller roles — only public chunks under RBAC-aware stores. */
    default List<RetrievedChunk> retrieve(String query, int topK) {
        return retrieve(query, topK, List.of());
    }

    /** Retrieves only chunks visible to the given caller {@code roles}. */
    List<RetrievedChunk> retrieve(String query, int topK, List<String> roles);

    /** Retrieves with RBAC and an optional exact-match metadata filter (all key/values must match). */
    default List<RetrievedChunk> retrieve(String query, int topK, List<String> roles,
                                          java.util.Map<String, String> metadataFilter) {
        return retrieve(query, topK, roles);
    }
}
