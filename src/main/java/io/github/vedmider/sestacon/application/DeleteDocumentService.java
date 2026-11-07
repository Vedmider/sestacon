package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.port.in.DeleteDocumentUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.logging.SecurityAudit;
import org.springframework.stereotype.Service;

import java.util.List;

/** Removes a document and all its chunks by stable id (all-or-nothing RBAC enforced by the store). */
@Service
public class DeleteDocumentService implements DeleteDocumentUseCase {

    private final DocumentStore documentStore;

    public DeleteDocumentService(DocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    @Override
    public int delete(String documentId, List<String> roles) {
        try {
            int removed = documentStore.deleteByDocumentId(documentId, roles);
            SecurityAudit.documentDeleted(documentId, removed);
            return removed;
        }
        catch (DocumentStore.DeleteNotPermittedException e) {
            SecurityAudit.deleteRefused(documentId);
            throw e;
        }
    }
}
