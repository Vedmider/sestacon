package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.port.out.DocumentParser;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIngestionServiceTest {

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
        };

        int count = new DocumentIngestionService(parser, store, false).ingest("doc.txt", "x".getBytes());

        assertThat(count).isEqualTo(2);
        assertThat(stored).isEqualTo(parsed);
    }

    @Test
    void appliesAclTagsToParsedChunks() {
        List<DocumentChunk> parsed = List.of(DocumentChunk.of("doc.txt", "a"));
        List<DocumentChunk> stored = new ArrayList<>();
        DocumentParser parser = (filename, content) -> parsed;
        DocumentStore store = new DocumentStore() {
            public void add(List<DocumentChunk> chunks) { stored.addAll(chunks); }
            public List<io.github.vedmider.sestacon.domain.model.RetrievedChunk> search(String q, int k) { return List.of(); }
        };

        new DocumentIngestionService(parser, store, false)
                .ingest("doc.txt", "x".getBytes(), List.of("engineering"));

        assertThat(stored.get(0).aclTags()).containsExactly("engineering");
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
        };

        new DocumentIngestionService(parser, store, true).ingest("guide.md", "x".getBytes());

        assertThat(stored.get(0).content())
                .startsWith("[guide.md > Limits]")
                .contains("max 100 req/s");
    }
}
