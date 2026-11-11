package io.github.vedmider.sestacon.domain.port.in;

import java.util.List;

/** Inbound port: ingest a document and return the number of chunks stored. */
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
    int ingest(String filename, byte[] content, List<String> aclTags, String sourceSystem);
}
