package com.example.rag.application;

import com.example.rag.domain.model.DocumentChunk;
import com.example.rag.domain.port.in.IngestChunksUseCase;
import com.example.rag.domain.port.out.DocumentStore;
import org.springframework.stereotype.Service;

import java.util.List;

/** Stores pre-parsed chunks that already conform to the standard contract. */
@Service
public class ChunkIngestionService implements IngestChunksUseCase {

    private final DocumentStore documentStore;

    public ChunkIngestionService(DocumentStore documentStore) {
        this.documentStore = documentStore;
    }

    @Override
    public int ingest(List<DocumentChunk> chunks) {
        documentStore.add(chunks);
        return chunks.size();
    }
}
