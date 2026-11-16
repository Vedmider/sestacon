package io.github.vedmider.sestacon.adapter.out.store;

import io.github.vedmider.sestacon.domain.model.DocumentChunk;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorDocumentStoreTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PgVectorDocumentStore store = new PgVectorDocumentStore(vectorStore);

    @Test
    void addMapsChunksToDocumentsWithSourceMetadata() {
        store.add(List.of(DocumentChunk.of("a.txt", "hello"),
                DocumentChunk.of("b.md", "world")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        List<Document> docs = captor.getValue();
        assertThat(docs).hasSize(2);
        assertThat(docs.get(0).getText()).isEqualTo("hello");
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "a.txt");
    }

    @Test
    void addWithNoChunksDoesNotCallStore() {
        store.add(List.of());
        verify(vectorStore, org.mockito.Mockito.never()).add(any());
    }

    @Test
    void searchMapsDocumentsToRetrievedChunksWithSourceAndScore() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                new Document("chunk text", Map.of("source", "a.txt")),
                new Document("no source here", Map.of())));

        List<RetrievedChunk> chunks = store.search("q", 2);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).isEqualTo("chunk text");
        assertThat(chunks.get(0).source()).isEqualTo("a.txt");
        assertThat(chunks.get(1).source()).isEqualTo("unknown");  // default when metadata absent
        assertThat(chunks.get(0).score()).isEqualTo(0.0);          // null score -> 0.0
    }

    @Test
    void metadataFilterIsAppliedAsAFilterExpression() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        store.search("q", 5, List.of(), Map.of("sourceSystem", "jira"));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression()).isNotNull();
    }

    @Test
    void noMetadataFilterLeavesTheExpressionNull() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        store.search("q", 5, List.of(), Map.of());

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        assertThat(captor.getValue().getFilterExpression()).isNull();
    }
}
