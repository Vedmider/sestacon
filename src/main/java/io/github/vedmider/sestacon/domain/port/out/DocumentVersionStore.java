package io.github.vedmider.sestacon.domain.port.out;

import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;

import java.util.Map;
import java.util.Optional;

/**
 * Outbound port tracking document version identity by content hash, decoupled from the retrieval
 * backend. It only compares and records; it never decides whether to re-ingest.
 */
public interface DocumentVersionStore {

    /** Classify the incoming section→hash against the stored version of {@code documentId}. */
    DocumentDiff diff(String documentId, Map<String, String> incomingSectionHashes);

    /** Record (upsert) the version identity after a successful (re-)ingest. */
    void record(String documentId, String docHash, long sizeBytes, Map<String, String> sectionHashes);

    /** The stored version of {@code documentId}, if any. */
    Optional<DocumentVersion> find(String documentId);

    /** Forget a document's version identity (called when its chunks are deleted). Returns rows removed. */
    int delete(String documentId);
}
