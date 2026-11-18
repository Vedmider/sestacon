package io.github.vedmider.sestacon.domain.port.in;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.DocumentDiff;

import java.util.List;

/**
 * Inbound port: ingest pre-parsed chunks that already conform to the standard contract
 * ({@link DocumentChunk}). This is the source-agnostic entry point — any producer (Jira,
 * logs, ERP, an external parser service) pushes chunks here, bypassing file parsing.
 */
public interface IngestChunksUseCase {

    default int ingest(List<DocumentChunk> chunks) {
        return ingest(chunks, "unknown");
    }

    /** Ingests chunks, recording {@code sourceSystem} as provenance metadata. */
    default int ingest(List<DocumentChunk> chunks, String sourceSystem) {
        return ingestWithVersions(chunks, sourceSystem).stored();
    }

    /** Ingests chunks and returns the stored count plus a per-document version diff. */
    IngestChunksResult ingestWithVersions(List<DocumentChunk> chunks, String sourceSystem);

    record IngestChunksResult(int stored, List<DocumentDiff> changes) {
    }
}
