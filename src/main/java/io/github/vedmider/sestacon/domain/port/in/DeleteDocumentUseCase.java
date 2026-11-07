package io.github.vedmider.sestacon.domain.port.in;

import java.util.List;

/**
 * Inbound port: remove a document (e.g. a superseded law) and all its chunks by stable
 * {@code documentId}. All-or-nothing RBAC — see
 * {@link io.github.vedmider.sestacon.domain.port.out.DocumentStore#deleteByDocumentId}.
 */
public interface DeleteDocumentUseCase {

    /** @return chunks removed (0 if the id did not exist). Throws if the caller can't see all chunks. */
    int delete(String documentId, List<String> roles);
}
