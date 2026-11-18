package io.github.vedmider.sestacon.domain.model;

import java.util.Map;

/** Stored version identity of a document: its aggregate hash, size, and per-section hashes. */
public record DocumentVersion(String documentId, String docHash, long sizeBytes, int chunkCount,
                              Map<String, String> sectionHashes) {
}
