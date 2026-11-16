package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import io.github.vedmider.sestacon.domain.port.out.DocumentStore;
import io.github.vedmider.sestacon.domain.port.out.Reranker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieval: wide first-pass recall from the {@link DocumentStore}, then a {@link Reranker}
 * second pass that re-orders to the requested {@code topK}. No cache, no LLM — grounded chunks only.
 */
@Service
public class RetrievalService implements RetrieveUseCase {

    private final DocumentStore documentStore;
    private final Reranker reranker;
    private final int recallFactor;
    private final double minScore;

    @org.springframework.beans.factory.annotation.Autowired
    public RetrievalService(DocumentStore documentStore, Reranker reranker,
                            @Value("${app.retrieval.recall-factor:4}") int recallFactor,
                            @Value("${app.retrieval.min-score:0.2}") double minScore) {
        this.documentStore = documentStore;
        this.reranker = reranker;
        this.recallFactor = Math.max(recallFactor, 1);
        this.minScore = minScore;
    }

    /** Defaults the no-confident-match floor; used by tests and non-Spring callers. */
    public RetrievalService(DocumentStore documentStore, Reranker reranker, int recallFactor) {
        this(documentStore, reranker, recallFactor, 0.2);
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, List<String> roles) {
        return retrieve(query, topK, roles, java.util.Map.of());
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK, List<String> roles,
                                         java.util.Map<String, String> metadataFilter) {
        int wide = Math.max(topK, 1) * recallFactor;
        List<RetrievedChunk> candidates = documentStore.search(query, wide, roles, metadataFilter);
        return reranker.rerank(query, candidates, topK).stream()
                .filter(c -> c.score() >= minScore)   // below the floor = no confident match -> omit
                .toList();
    }
}
