package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.port.in.DocumentVersionUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Reads stored document version identity so a caller can detect changes without re-uploading. */
@Service
public class DocumentVersionService implements DocumentVersionUseCase {

    private final DocumentVersionStore versionStore;
    private final DocumentStore documentStore;

    public DocumentVersionService(DocumentVersionStore versionStore, DocumentStore documentStore) {
        this.versionStore = versionStore;
        this.documentStore = documentStore;
    }

    @Override
    public Optional<DocumentVersion> version(String documentId, List<String> roles) {
        if (!documentStore.isReadable(documentId, roles)) {
            return Optional.empty();   // restricted or unknown: indistinguishable to the caller
        }
        return versionStore.find(documentId);
    }
}
