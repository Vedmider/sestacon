package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.adapter.out.enrich.RuleMetadataEnricher;
import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.port.out.AnswerCache;
import io.github.vedmider.sestacon.domain.port.out.DocumentParser;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import io.github.vedmider.sestacon.domain.port.out.MetadataEnricher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIngestionServiceTest {

    private final MetadataEnricher enricher = new RuleMetadataEnricher();

    /** Fresh store with no prior version → every ingest classifies NEW and proceeds to store. */
    private static DocumentVersionStore newVersionStore() {
        return new DocumentVersionStore() {
            public DocumentDiff diff(String id, Map<String, String> in) {
                return DocumentDiff.compare(id, Map.of(), in);
            }

            public void record(String id, String h, long s, Map<String, String> m) {
            }

            public Optional<DocumentVersion> find(String id) {
                return Optional.empty();
            }

            public int delete(String id) {
                return 0;
            }
        };
    }

    @Test
    void parsesThenStoresAndReturnsChunkCount() {
        List<DocumentChunk> parsed = List.of(
                DocumentChunk.of("doc.txt", "a"),
                DocumentChunk.of("doc.txt", "b"));
        List<DocumentChunk> stored = new ArrayList<>();

        DocumentParser parser = (filename, content) -> parsed;
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
            public List<io.github.vedmider.sestacon.domain.model.RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 0; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { stored.addAll(chunks); }
        };

        int count = new DocumentIngestionService(parser, store, newVersionStore(), enricher, false)
                .ingest("doc.txt", "x".getBytes());

        assertThat(count).isEqualTo(2);
        assertThat(stored).extracting(DocumentChunk::content).containsExactly("a", "b");
        assertThat(stored.get(0).metadata()).containsEntry("docType", "txt").containsKey("ingestedAt");
    }

    @Test
    void appliesAclTagsToParsedChunks() {
        List<DocumentChunk> parsed = List.of(DocumentChunk.of("doc.txt", "a"));
        List<DocumentChunk> stored = new ArrayList<>();
        DocumentParser parser = (filename, content) -> parsed;
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
            public List<io.github.vedmider.sestacon.domain.model.RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 0; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { stored.addAll(chunks); }
        };

        new DocumentIngestionService(parser, store, newVersionStore(), enricher, false)
                .ingest("doc.txt", "x".getBytes(), List.of("engineering"));

        assertThat(stored.get(0).aclTags()).containsExactly("engineering");
    }

    @Test
    void recordsSourceSystemWhenProvided() {
        List<DocumentChunk> parsed = List.of(DocumentChunk.of("doc.txt", "a"));
        List<DocumentChunk> stored = new ArrayList<>();
        DocumentParser parser = (filename, content) -> parsed;
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
            public List<io.github.vedmider.sestacon.domain.model.RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 0; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { stored.addAll(chunks); }
        };

        new DocumentIngestionService(parser, store, newVersionStore(), enricher, false)
                .ingest("doc.txt", "x".getBytes(), List.of(), "snowflake");

        assertThat(stored.get(0).metadata()).containsEntry("sourceSystem", "snowflake");
    }

    @Test
    void contextualIngestPrependsDocSectionHeaderToChunkContent() {
        List<DocumentChunk> parsed = List.of(new DocumentChunk(
                "guide.md", "Limits", "max 100 req/s", java.util.Map.of(), List.of("public")));
        List<DocumentChunk> stored = new ArrayList<>();
        DocumentParser parser = (filename, content) -> parsed;
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
            public List<io.github.vedmider.sestacon.domain.model.RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 0; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { stored.addAll(chunks); }
        };

        new DocumentIngestionService(parser, store, newVersionStore(), enricher, true).ingest("guide.md", "x".getBytes());

        assertThat(stored.get(0).content())
                .startsWith("[guide.md > Limits]")
                .contains("max 100 req/s");
    }

    @Test
    void unchangedReingestSkipsStoreAndReportsUnchanged() {
        List<DocumentChunk> parsed = List.of(DocumentChunk.of("doc.txt", "a"));
        List<DocumentChunk> stored = new ArrayList<>();
        DocumentParser parser = (filename, content) -> parsed;
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
            public List<io.github.vedmider.sestacon.domain.model.RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 0; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { stored.addAll(chunks); }
        };
        // stateful version store: records on first ingest, so the second ingest is UNCHANGED
        DocumentVersionStore versionStore = new DocumentVersionStore() {
            final Map<String, String> state = new LinkedHashMap<>();

            public DocumentDiff diff(String id, Map<String, String> in) {
                return DocumentDiff.compare(id, new LinkedHashMap<>(state), in);
            }

            public void record(String id, String h, long s, Map<String, String> m) {
                state.clear();
                state.putAll(m);
            }

            public Optional<DocumentVersion> find(String id) { return Optional.empty(); }

            public int delete(String id) { return 0; }
        };
        var service = new DocumentIngestionService(parser, store, versionStore, enricher, false);

        var first = service.ingestWithVersion("doc.txt", "x".getBytes(), List.of(), "unknown");
        var second = service.ingestWithVersion("doc.txt", "x".getBytes(), List.of(), "unknown");

        assertThat(first.diff().status()).isEqualTo(DocumentDiff.Status.NEW);
        assertThat(second.diff().status()).isEqualTo(DocumentDiff.Status.UNCHANGED);
        assertThat(stored).hasSize(1);   // second ingest did not re-store
    }

    private static DocumentVersionStore statefulVersionStore() {
        return new DocumentVersionStore() {
            final Map<String, String> state = new LinkedHashMap<>();

            public DocumentDiff diff(String id, Map<String, String> in) {
                return DocumentDiff.compare(id, new LinkedHashMap<>(state), in);
            }

            public void record(String id, String h, long s, Map<String, String> m) {
                state.clear();
                state.putAll(m);
            }

            public Optional<DocumentVersion> find(String id) {
                return Optional.empty();
            }

            public int delete(String id) {
                return 0;
            }
        };
    }

    private static DocumentStore recordingStore(List<DocumentChunk> stored) {
        return new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
            public List<io.github.vedmider.sestacon.domain.model.RetrievedChunk> search(String q, int k) { return List.of(); }
            public int deleteByDocumentId(String id, List<String> roles) { return 0; }
            public void replace(List<DocumentChunk> chunks, List<String> roles) { stored.addAll(chunks); }
        };
    }

    @Test
    void reingestingIdenticalContentWithNewAclTagsRestoresTheChunksWithTheNewAcl() {
        List<DocumentChunk> stored = new ArrayList<>();
        DocumentParser parser = (filename, content) -> List.of(DocumentChunk.of("doc.txt", "a"));
        DocumentIngestionService service = new DocumentIngestionService(
                parser, recordingStore(stored), statefulVersionStore(), enricher, false);

        service.ingest("doc.txt", "x".getBytes());
        var restricted = service.ingestWithVersion("doc.txt", "x".getBytes(), List.of("legal"), null);

        assertThat(restricted.diff().status()).isNotEqualTo(DocumentDiff.Status.UNCHANGED);
        assertThat(stored).hasSize(2);
        assertThat(stored.get(1).aclTags()).containsExactly("legal");
    }

    @Test
    void corpusChangeInvalidatesTheAnswerCacheButAnUnchangedReingestDoesNot() {
        List<DocumentChunk> stored = new ArrayList<>();
        int[] invalidations = {0};
        AnswerCache cache = new AnswerCache() {
            public Optional<io.github.vedmider.sestacon.domain.model.CachedAnswer> findSimilar(float[] e, List<String> r) {
                return Optional.empty();
            }
            public void store(io.github.vedmider.sestacon.domain.model.CachedAnswer a, List<String> r) {
            }
            public void invalidateAll() {
                invalidations[0]++;
            }
        };
        DocumentParser parser = (filename, content) -> List.of(DocumentChunk.of("doc.txt", "a"));
        DocumentIngestionService service = new DocumentIngestionService(
                parser, recordingStore(stored), statefulVersionStore(), enricher, false, cache);

        service.ingest("doc.txt", "x".getBytes());
        service.ingest("doc.txt", "x".getBytes());

        assertThat(invalidations[0]).as("only the NEW ingest changes the corpus").isEqualTo(1);
    }
}
