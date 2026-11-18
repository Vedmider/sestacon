package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.port.in.DocumentVersionUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Reads stored document version identity so a caller can detect changes without re-uploading. */
@Service
public class DocumentVersionService implements DocumentVersionUseCase {

    private final DocumentVersionStore versionStore;

    public DocumentVersionService(DocumentVersionStore versionStore) {
        this.versionStore = versionStore;
    }

    @Override
    public Optional<DocumentVersion> version(String documentId) {
        return versionStore.find(documentId);
    }
}
