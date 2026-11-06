package com.example.rag.application;

import com.example.rag.domain.model.ContextualChunker;
import com.example.rag.domain.model.DocumentChunk;
import com.example.rag.domain.port.in.IngestChunksUseCase;
import com.example.rag.domain.port.out.DocumentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Stores pre-parsed chunks that already conform to the standard contract. */
@Service
public class ChunkIngestionService implements IngestChunksUseCase {

    private final DocumentStore documentStore;
    private final boolean contextual;

    public ChunkIngestionService(DocumentStore documentStore,
                                 @Value("${app.ingest.contextual:true}") boolean contextual) {
        this.documentStore = documentStore;
        this.contextual = contextual;
    }

    @Override
    public int ingest(List<DocumentChunk> chunks) {
        List<DocumentChunk> toStore = contextual ? ContextualChunker.contextualize(chunks) : chunks;
        documentStore.add(toStore);
        return toStore.size();
    }
}
