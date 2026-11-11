package io.github.vedmider.sestacon.domain.port.out;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;

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

    /**
     * Retrieves with RBAC and an optional exact-match metadata filter (every key/value in
     * {@code metadataFilter} must be present on a chunk's metadata). Stores that cannot filter on
     * metadata ignore it and fall back to the RBAC search.
     */
    default List<RetrievedChunk> search(String query, int topK, List<String> roles,
                                        java.util.Map<String, String> metadataFilter) {
        return search(query, topK, roles);
    }

    /**
     * Deletes every chunk of {@code documentId}, enforcing <b>all-or-nothing</b> RBAC: the delete
     * proceeds only if {@code roles} (plus {@code "public"}) cover <i>all</i> chunks of the document.
     * If any chunk is not visible to the caller, nothing is deleted and
     * {@link DeleteNotPermittedException} is thrown. Deleting a non-existent id is a no-op (0).
     *
     * @return number of chunks removed (0 if the id did not exist)
     */
    int deleteByDocumentId(String documentId, List<String> roles);

    /**
     * Atomically replaces each document in {@code chunks}: for every distinct {@code documentId}
     * present, its existing chunks are deleted (all-or-nothing RBAC) and the new chunks inserted,
     * in a single transaction. This is the supersession primitive — re-ingesting a document id
     * overwrites it rather than appending duplicates.
     */
    void replace(List<DocumentChunk> chunks, List<String> roles);

    /** Thrown when a caller's roles do not cover every chunk of a document they tried to delete. */
    class DeleteNotPermittedException extends RuntimeException {
        public DeleteNotPermittedException(String documentId) {
            super("insufficient roles to delete all chunks of document: " + documentId);
        }
    }
}
