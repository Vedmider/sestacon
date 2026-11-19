package io.github.vedmider.sestacon.adapter.out.rerank;

import io.github.vedmider.sestacon.domain.port.out.Reranker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Default reranker: deterministic and model-free; replace the bean with a cross-encoder for stronger precision. */
@Configuration
public class RerankerConfig {

    @Bean
    @ConditionalOnMissingBean(Reranker.class)
    public Reranker lexicalReranker(
            @Value("${app.rerank.min-lexical-coverage:" + LexicalReranker.DEFAULT_MIN_LEXICAL_COVERAGE + "}")
            double minLexicalCoverage,
            @Value("${app.rerank.vector-trusted-for-non-latin:false}") boolean vectorTrustedForNonLatin) {
        return new LexicalReranker(minLexicalCoverage, vectorTrustedForNonLatin);
    }
}
