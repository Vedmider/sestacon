package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.adapter.out.enrich.RuleMetadataEnricher;
import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.DocumentDiff;
import io.github.vedmider.sestacon.domain.model.DocumentVersion;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.DocumentVersionStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkIngestionServiceTest {

    private final RuleMetadataEnricher enricher = new RuleMetadataEnricher();
    private final List<DocumentChunk> stored = new ArrayList<>();

    private final DocumentStore store = new DocumentStore() {
        public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
        public List<RetrievedChunk> search(String q, int k) { return List.of(); }
        public int deleteByDocumentId(String id, List<String> roles) { return 0; }
        public void replace(List<DocumentChunk> chunks, List<String> roles) { stored.addAll(chunks); }
    };

    /** Stateful: records per-document section maps so a re-ingest can be classified UNCHANGED. */
    private final Map<String, Map<String, String>> versions = new HashMap<>();
    private final DocumentVersionStore versionStore = new DocumentVersionStore() {
        public DocumentDiff diff(String id, Map<String, String> in) {
            return DocumentDiff.compare(id, versions.getOrDefault(id, Map.of()), in);
        }

        public void record(String id, String h, long s, Map<String, String> m) {
            versions.put(id, new LinkedHashMap<>(m));
        }

        public Optional<DocumentVersion> find(String id) { return Optional.empty(); }

        public int delete(String id) { return versions.remove(id) != null ? 1 : 0; }
    };

    private ChunkIngestionService service() {
        return new ChunkIngestionService(store, versionStore, enricher, false);
    }

    @Test
    void eachDocumentInABatchGetsItsOwnNewDiffAndIsRecorded() {
        var result = service().ingestWithVersions(List.of(
                new DocumentChunk("a.pdf", "Ch1", "alpha", Map.of(), List.of("public")),
                new DocumentChunk("a.pdf", "Ch2", "beta", Map.of(), List.of("public")),
                new DocumentChunk("b.pdf", "", "gamma", Map.of(), List.of("public"))), "unknown");

        assertThat(result.stored()).isEqualTo(3);
        assertThat(result.changes()).extracting(DocumentDiff::documentId)
                .containsExactlyInAnyOrder("a.pdf", "b.pdf");
        assertThat(result.changes()).allMatch(d -> d.status() == DocumentDiff.Status.NEW);
        assertThat(versions.keySet()).containsExactlyInAnyOrder("a.pdf", "b.pdf");
        assertThat(stored).hasSize(3);
    }

    @Test
    void duplicateSectionPathsDoNotCollideAndReingestIsUnchanged() {
        List<DocumentChunk> batch = List.of(
                new DocumentChunk("dup.pdf", "", "one", Map.of(), List.of("public")),
                new DocumentChunk("dup.pdf", "", "two", Map.of(), List.of("public")));

        var first = service().ingestWithVersions(batch, "unknown");
        var second = service().ingestWithVersions(batch, "unknown");

        // both blank-sectionPath chunks are tracked (keyed by ordinal), so it is NEW then UNCHANGED
        assertThat(first.changes().get(0).status()).isEqualTo(DocumentDiff.Status.NEW);
        assertThat(versions.get("dup.pdf")).hasSize(2);
        assertThat(second.changes().get(0).status()).isEqualTo(DocumentDiff.Status.UNCHANGED);
    }
}
