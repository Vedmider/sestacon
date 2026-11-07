package io.github.vedmider.sestacon.adapter.out.rerank;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.out.Reranker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default reranker: blends the first-pass vector score with query-term overlap in the chunk text,
 * so chunks that actually contain the query's words are lifted above vector-only near-misses.
 * Deterministic, model-free. Replace the bean with a cross-encoder adapter for stronger precision.
 */
@Configuration
public class RerankerConfig {

    @Bean
    @ConditionalOnMissingBean(Reranker.class)
    public Reranker lexicalReranker() {
        return (query, candidates, topK) -> {
            Set<String> terms = tokenize(query);
            return candidates.stream()
                    .sorted(Comparator.comparingDouble((RetrievedChunk c) -> blended(c, terms)).reversed())
                    .limit(Math.max(topK, 0))
                    .toList();
        };
    }

    private static double blended(RetrievedChunk c, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return c.score();
        }
        Set<String> chunkTerms = tokenize(c.text());
        long hits = queryTerms.stream().filter(chunkTerms::contains).count();
        double overlap = (double) hits / queryTerms.size();   // 0..1
        return c.score() + overlap;                            // lexical overlap breaks vector ties
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());
    }
}
