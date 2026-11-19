package io.github.vedmider.sestacon.adapter.out.judge;

import io.github.vedmider.sestacon.domain.model.TextAnalyzer;
import io.github.vedmider.sestacon.domain.port.out.Judge;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.stream.Collectors;

/** Default judge: deterministic proxy (content-term grounding and question coverage); swap in an LLM judge for semantics. */
@Configuration
public class JudgeConfig {

    @Bean
    @ConditionalOnMissingBean(Judge.class)
    public Judge lexicalJudge() {
        return (question, answer, contexts) -> {
            Set<String> answerTerms = TextAnalyzer.terms(answer);
            Set<String> questionTerms = TextAnalyzer.terms(question);
            Set<String> contextTerms = contexts == null ? Set.of()
                    : contexts.stream().flatMap(c -> TextAnalyzer.terms(c).stream()).collect(Collectors.toSet());

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
}
