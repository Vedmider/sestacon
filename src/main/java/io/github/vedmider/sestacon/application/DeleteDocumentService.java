package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.port.in.DeleteDocumentUseCase;
import io.github.vedmider.sestacon.domain.port.out.AnswerCache;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import io.github.vedmider.sestacon.logging.SecurityAudit;
import org.springframework.stereotype.Service;

import java.util.List;

/** Removes a document and all its chunks by stable id (all-or-nothing RBAC enforced by the store). */
@Service
public class DeleteDocumentService implements DeleteDocumentUseCase {

    private final DocumentStore documentStore;
    private final DocumentVersionStore versionStore;

    private final AnswerCache answerCache;

    public DeleteDocumentService(DocumentStore documentStore, DocumentVersionStore versionStore) {
        this(documentStore, versionStore, new AnswerCache.None());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DeleteDocumentService(DocumentStore documentStore, DocumentVersionStore versionStore,
                                 AnswerCache answerCache) {
        this.documentStore = documentStore;
        this.versionStore = versionStore;
        this.answerCache = answerCache;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public int delete(String documentId, List<String> roles) {
        documentStore.lockDocuments(List.of(documentId));
        try {
            int removed = documentStore.deleteByDocumentId(documentId, roles);
            versionStore.delete(documentId);
            answerCache.invalidateAll();
            SecurityAudit.documentDeleted(documentId, removed);
            return removed;
        }
        catch (DocumentStore.DeleteNotPermittedException e) {
            // Opaque: a caller who cannot see the whole document gets the same 0 as an unknown id,
            // so a restricted document's existence is not revealed (no 403 oracle).
            SecurityAudit.deleteRefused(documentId);
            return 0;
        }
    }
}
