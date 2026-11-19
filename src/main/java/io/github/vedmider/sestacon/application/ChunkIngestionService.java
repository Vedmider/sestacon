package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.ContentHash;
import io.github.vedmider.sestacon.domain.model.ContextualChunker;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.port.in.IngestChunksUseCase;
import io.github.vedmider.sestacon.domain.port.out.AnswerCache;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import io.github.vedmider.sestacon.domain.port.out.MetadataEnricher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stores pre-parsed chunks that already conform to the standard contract, with version tracking. */
@Service
public class ChunkIngestionService implements IngestChunksUseCase {

    private final DocumentStore documentStore;
    private final DocumentVersionStore versionStore;
    private final MetadataEnricher enricher;
    private final boolean contextual;
    private final AnswerCache answerCache;

    public ChunkIngestionService(DocumentStore documentStore, DocumentVersionStore versionStore,
                                 MetadataEnricher enricher, boolean contextual) {
        this(documentStore, versionStore, enricher, contextual, new AnswerCache.None());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChunkIngestionService(DocumentStore documentStore, DocumentVersionStore versionStore,
                                 MetadataEnricher enricher,
                                 @Value("${app.ingest.contextual:true}") boolean contextual,
                                 AnswerCache answerCache) {
        this.answerCache = answerCache;
        this.documentStore = documentStore;
        this.versionStore = versionStore;
        this.enricher = enricher;
        this.contextual = contextual;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public IngestChunksResult ingestWithVersions(List<DocumentChunk> chunks, String sourceSystem) {
        List<DocumentChunk> enriched = enricher.enrich(chunks, sourceSystem);
        List<DocumentChunk> toStore = contextual ? ContextualChunker.contextualize(enriched) : enriched;

        // A batch may carry several documentIds, and chunk sectionPaths may be blank/duplicated, so key
        // each document's version sections by ordinal (stable, unique) rather than the section path.
        Map<String, LinkedHashMap<String, String>> perDoc = new LinkedHashMap<>();
        Map<String, Long> sizes = new LinkedHashMap<>();
        Map<String, Integer> ordinal = new HashMap<>();
        for (DocumentChunk c : toStore) {
            String doc = c.documentId();
            int i = ordinal.merge(doc, 1, Integer::sum) - 1;
            perDoc.computeIfAbsent(doc, k -> new LinkedHashMap<>())
                    .put(i + ":" + c.sectionPath(), ContentHash.ofSection(c.content(), c.aclTags()));
            sizes.merge(doc, (long) c.content().getBytes(StandardCharsets.UTF_8).length, Long::sum);
        }

        documentStore.lockDocuments(perDoc.keySet());
        List<DocumentDiff> diffs = new ArrayList<>();
        for (Map.Entry<String, LinkedHashMap<String, String>> e : perDoc.entrySet()) {
            diffs.add(versionStore.diff(e.getKey(), e.getValue()));
        }

        List<String> aclTags = toStore.stream()
                .flatMap(c -> c.aclTags().stream()).distinct().toList();
        documentStore.replace(toStore, aclTags);

        for (Map.Entry<String, LinkedHashMap<String, String>> e : perDoc.entrySet()) {
            versionStore.record(e.getKey(),
                    ContentHash.ofChunks(new ArrayList<>(e.getValue().values())),
                    sizes.getOrDefault(e.getKey(), 0L), e.getValue());
        }
        answerCache.invalidateAll();
        return new IngestChunksResult(toStore.size(), diffs);
    }
}
