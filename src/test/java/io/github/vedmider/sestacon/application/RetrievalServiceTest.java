package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.adapter.out.rerank.RerankerConfig;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.Reranker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final DocumentStore store = mock(DocumentStore.class);
    private final Reranker reranker = new RerankerConfig().lexicalReranker();

    @Test
    void retrievesWideThenReranksToTopK() {
        // Vector order puts a near-miss first; the lexically-relevant chunk is third.
        when(store.search(eq("rate limiting"), eq(12), anyList(), anyMap())).thenReturn(List.of(
                new RetrievedChunk("caching of answers", "cache.md", 0.90),
                new RetrievedChunk("embedding provider config", "embed.md", 0.88),
                new RetrievedChunk("token bucket rate limiting per client", "ratelimit.md", 0.80)));

        RetrievalService svc = new RetrievalService(store, reranker, 4);
        List<RetrievedChunk> out = svc.retrieve("rate limiting", 3, List.of());

        // Store was queried WIDE (topK*factor = 3*4 = 12), not just topK.
        verify(store).search(eq("rate limiting"), eq(12), anyList(), anyMap());
        // Reranker lifted the lexically-matching chunk to rank 1.
        assertThat(out.get(0).source()).isEqualTo("ratelimit.md");
        assertThat(out).hasSize(3);
    }

    @Test
    void honoursTopKLimitAfterRerank() {
        when(store.search(eq("q"), eq(8), anyList(), anyMap())).thenReturn(List.of(
                new RetrievedChunk("a", "a.md", 0.5),
                new RetrievedChunk("b", "b.md", 0.4),
                new RetrievedChunk("c", "c.md", 0.3)));

        RetrievalService svc = new RetrievalService(store, reranker, 4);
        List<RetrievedChunk> out = svc.retrieve("q", 2, List.of());

        assertThat(out).hasSize(2);
    }

    @Test
    void recallFactorIsClampedToAtLeastOne() {
        when(store.search(eq("q"), eq(3), anyList(), anyMap())).thenReturn(List.of(
                new RetrievedChunk("x", "x.md", 0.1)));

        RetrievalService svc = new RetrievalService(store, reranker, 0);  // clamped to 1
        svc.retrieve("q", 3, List.of());

        verify(store).search(eq("q"), eq(3), anyList(), anyMap());
    }
}
