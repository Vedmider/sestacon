package io.github.vedmider.sestacon.domain.port.in;

import io.github.vedmider.sestacon.domain.model.DocumentVersion;

import java.util.Optional;

/** Inbound port: read a document's stored version identity (hashes/size) for change detection. */
public interface DocumentVersionUseCase {

    /** Returns the version identity only if {@code roles} may read the whole document; empty otherwise. */
    Optional<DocumentVersion> version(String documentId, java.util.List<String> roles);
}
