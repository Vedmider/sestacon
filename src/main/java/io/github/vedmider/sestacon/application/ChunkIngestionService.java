package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.ContextualChunker;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.in.IngestChunksUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.MetadataEnricher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Stores pre-parsed chunks that already conform to the standard contract. */
@Service
public class ChunkIngestionService implements IngestChunksUseCase {

    private final DocumentStore documentStore;
    private final MetadataEnricher enricher;
    private final boolean contextual;

    public ChunkIngestionService(DocumentStore documentStore, MetadataEnricher enricher,
                                 @Value("${app.ingest.contextual:true}") boolean contextual) {
        this.documentStore = documentStore;
        this.enricher = enricher;
        this.contextual = contextual;
    }

    @Override
    public int ingest(List<DocumentChunk> chunks, String sourceSystem) {
        List<DocumentChunk> enriched = enricher.enrich(chunks, sourceSystem);
        List<DocumentChunk> toStore = contextual ? ContextualChunker.contextualize(enriched) : enriched;
        List<String> aclTags = toStore.stream()
                .flatMap(c -> c.aclTags().stream()).distinct().toList();
        documentStore.replace(toStore, aclTags);
        return toStore.size();
    }
}
