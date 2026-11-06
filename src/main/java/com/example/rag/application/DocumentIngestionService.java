package com.example.rag.application;

import com.example.rag.domain.model.ContextualChunker;
import com.example.rag.domain.model.DocumentChunk;
import com.example.rag.domain.port.in.DocumentIngestionUseCase;
import com.example.rag.domain.port.out.DocumentParser;
import com.example.rag.domain.port.out.DocumentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Parses an uploaded document into chunks and stores them. Depends only on ports. */
@Service
public class DocumentIngestionService implements DocumentIngestionUseCase {

    private final DocumentParser parser;
    private final DocumentStore documentStore;
    private final boolean contextual;

    public DocumentIngestionService(DocumentParser parser, DocumentStore documentStore,
                                    @Value("${app.ingest.contextual:true}") boolean contextual) {
        this.parser = parser;
        this.documentStore = documentStore;
        this.contextual = contextual;
    }

    @Override
    public int ingest(String filename, byte[] content, List<String> aclTags) {
        List<DocumentChunk> chunks = parser.parse(filename, content);
        if (aclTags != null && !aclTags.isEmpty()) {
            chunks = chunks.stream()
                    .map(c -> new DocumentChunk(c.documentId(), c.sectionPath(), c.content(),
                            c.metadata(), aclTags))
                    .toList();
        }
        if (contextual) {
            chunks = ContextualChunker.contextualize(chunks);
        }
        documentStore.add(chunks);
        return chunks.size();
    }
}
