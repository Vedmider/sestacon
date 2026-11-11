package io.github.vedmider.sestacon.domain.port.out;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;

import java.util.List;

/**
 * Derives extra metadata for chunks at ingest (doc type, language, content tags, provenance).
 * Rule-based and deterministic — no LLM — so it stays provider-agnostic and cheap.
 */
public interface MetadataEnricher {

    /** Returns copies of {@code chunks} with derived metadata added; caller-set keys are preserved. */
    List<DocumentChunk> enrich(List<DocumentChunk> chunks, String sourceSystem);
}
