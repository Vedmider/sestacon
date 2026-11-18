package io.github.vedmider.sestacon.domain.port.in;

import io.github.vedmider.sestacon.domain.model.IngestResult;

import java.util.List;

/** Inbound port: ingest a document, returning the chunks stored and the change vs the prior version. */
public interface DocumentIngestionUseCase {

    /** Ingests with no access restriction — chunks are tagged public. */
    default int ingest(String filename, byte[] content) {
        return ingest(filename, content, List.of());
    }

    /** Ingests, tagging every chunk with {@code aclTags} (empty = public) for RBAC retrieval. */
    default int ingest(String filename, byte[] content, List<String> aclTags) {
        return ingest(filename, content, aclTags, "unknown");
    }

    /** Ingests with acl tags and records {@code sourceSystem} as provenance metadata. */
    default int ingest(String filename, byte[] content, List<String> aclTags, String sourceSystem) {
        return ingestWithVersion(filename, content, aclTags, sourceSystem).chunks();
    }

    /** Ingests and returns the chunk count plus the version diff (NEW/UNCHANGED/FULL/PARTIAL). */
    IngestResult ingestWithVersion(String filename, byte[] content, List<String> aclTags,
                                   String sourceSystem);
}
