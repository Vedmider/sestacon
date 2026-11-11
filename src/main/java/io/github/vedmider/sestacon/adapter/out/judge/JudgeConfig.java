package io.github.vedmider.sestacon.adapter.out.judge;

import io.github.vedmider.sestacon.domain.port.out.Judge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default judge: deterministic, model-free. Faithfulness = share of answer terms grounded in the
 * contexts; answer-relevance = share of question terms covered by the answer. A real LLM judge
 * (replacing this bean) gives stronger semantic scoring; this keeps the harness runnable in CI.
 */
@Configuration
public class JudgeConfig {

    @Bean
    @ConditionalOnMissingBean(Judge.class)
    public Judge lexicalJudge() {
        return (question, answer, contexts) -> {
            Set<String> answerTerms = tokenize(answer);
            Set<String> questionTerms = tokenize(question);
            Set<String> contextTerms = contexts == null ? Set.of()
                    : contexts.stream().flatMap(c -> tokenize(c).stream()).collect(Collectors.toSet());

            double faithfulness = share(answerTerms, contextTerms);
            double answerRelevance = share(questionTerms, answerTerms);
            String rationale = "grounded=%.2f relevant=%.2f".formatted(faithfulness, answerRelevance);
            return new Judge.Verdict(faithfulness, answerRelevance, rationale);
        };
    }

    private static double share(Set<String> of, Set<String> in) {
        if (of.isEmpty()) {
            return 0.0;
        }
        long hits = of.stream().filter(in::contains).count();
        return (double) hits / of.size();
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
