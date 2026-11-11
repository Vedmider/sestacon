package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.adapter.out.enrich.RuleMetadataEnricher;
import io.github.vedmider.sestacon.domain.port.out.DocumentParser;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.MetadataEnricher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIngestionServiceTest {

    private final MetadataEnricher enricher = new RuleMetadataEnricher();

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

        int count = new DocumentIngestionService(parser, store, enricher, false)
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

        new DocumentIngestionService(parser, store, enricher, false)
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

        new DocumentIngestionService(parser, store, enricher, false)
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

        new DocumentIngestionService(parser, store, enricher, true).ingest("guide.md", "x".getBytes());

        assertThat(stored.get(0).content())
                .startsWith("[guide.md > Limits]")
                .contains("max 100 req/s");
    }
}
