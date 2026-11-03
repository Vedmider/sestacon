package com.example.rag.domain.port.in;

import com.example.rag.domain.model.DocumentChunk;

import java.util.List;

/**
 * Inbound port: ingest pre-parsed chunks that already conform to the standard contract
 * ({@link DocumentChunk}). This is the source-agnostic entry point — any producer (Jira,
 * logs, ERP, an external parser service) pushes chunks here, bypassing file parsing.
 */
public interface IngestChunksUseCase {

    int ingest(List<DocumentChunk> chunks);
}
