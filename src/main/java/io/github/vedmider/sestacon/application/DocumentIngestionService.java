package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.ContextualChunker;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.in.DocumentIngestionUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentParser;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.MetadataEnricher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/** Parses an uploaded document into chunks and stores them. Depends only on ports. */
@Service
public class DocumentIngestionService implements DocumentIngestionUseCase {

    private final DocumentParser parser;
    private final DocumentStore documentStore;
    private final MetadataEnricher enricher;
    private final boolean contextual;

    public DocumentIngestionService(DocumentParser parser, DocumentStore documentStore,
                                    MetadataEnricher enricher,
                                    @Value("${app.ingest.contextual:true}") boolean contextual) {
        this.parser = parser;
        this.documentStore = documentStore;
        this.enricher = enricher;
        this.contextual = contextual;
    }

    @Override
    public int ingest(String filename, byte[] content, List<String> aclTags, String sourceSystem) {
        List<DocumentChunk> chunks = parser.parse(filename, content);
        if (aclTags != null && !aclTags.isEmpty()) {
            chunks = chunks.stream()
                    .map(c -> new DocumentChunk(c.documentId(), c.sectionPath(), c.content(),
                            c.metadata(), aclTags))
                    .toList();
        }
        chunks = enricher.enrich(chunks, sourceSystem);
        if (contextual) {
            chunks = ContextualChunker.contextualize(chunks);
        }
        documentStore.replace(chunks, aclTags == null ? List.of() : aclTags);
        return chunks.size();
    }
}
