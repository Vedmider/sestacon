package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.ContentHash;
import io.github.vedmider.sestacon.domain.model.ContextualChunker;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.IngestResult;
import io.github.vedmider.sestacon.domain.port.in.DocumentIngestionUseCase;
import io.github.vedmider.sestacon.domain.port.out.AnswerCache;
import io.github.vedmider.sestacon.domain.port.out.DocumentParser;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import io.github.vedmider.sestacon.domain.port.out.MetadataEnricher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses an uploaded document into chunks and stores them. Depends only on ports. */
@Service
public class DocumentIngestionService implements DocumentIngestionUseCase {

    private final DocumentParser parser;
    private final DocumentStore documentStore;
    private final DocumentVersionStore versionStore;
    private final MetadataEnricher enricher;
    private final boolean contextual;
    private final AnswerCache answerCache;

    public DocumentIngestionService(DocumentParser parser, DocumentStore documentStore,
                                    DocumentVersionStore versionStore, MetadataEnricher enricher,
                                    boolean contextual) {
        this(parser, documentStore, versionStore, enricher, contextual, new AnswerCache.None());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentIngestionService(DocumentParser parser, DocumentStore documentStore,
                                    DocumentVersionStore versionStore, MetadataEnricher enricher,
                                    @Value("${app.ingest.contextual:true}") boolean contextual,
                                    AnswerCache answerCache) {
        this.answerCache = answerCache;
        this.parser = parser;
        this.documentStore = documentStore;
        this.versionStore = versionStore;
        this.enricher = enricher;
        this.contextual = contextual;
    }

    @Override
    public IngestResult ingestWithVersion(String filename, byte[] content, List<String> aclTags,
                                          String sourceSystem) {
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

        String documentId = chunks.isEmpty() ? filename : chunks.get(0).documentId();
        Map<String, String> sectionHashes = new LinkedHashMap<>();
        List<String> orderedHashes = new ArrayList<>(chunks.size());
        for (DocumentChunk c : chunks) {
            String hash = ContentHash.ofSection(c.content(), c.aclTags());
            sectionHashes.put(c.sectionPath(), hash);
            orderedHashes.add(hash);
        }

        DocumentDiff diff = versionStore.diff(documentId, sectionHashes);
        if (diff.status() == DocumentDiff.Status.UNCHANGED) {
            return new IngestResult(chunks.size(), diff);   // identical content: skip re-embed/re-store
        }
        documentStore.replace(chunks, aclTags == null ? List.of() : aclTags);
        versionStore.record(documentId, ContentHash.ofChunks(orderedHashes),
                content.length, sectionHashes);
        answerCache.invalidateAll();
        return new IngestResult(chunks.size(), diff);
    }
}
