package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.domain.model.Answer;
import io.github.vedmider.sestacon.domain.model.Refusal;
import io.github.vedmider.sestacon.domain.model.TextAnalyzer;
import io.github.vedmider.sestacon.domain.port.in.RagQueryUseCase;
import io.github.vedmider.sestacon.domain.port.out.Judge;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** End-to-end answer eval: grounding is judged on the contexts the answer used; refusals and correctness are scored. */
@Component
public class JudgeEvalHarness {

    private final RagQueryUseCase ragQuery;
    private final Judge judge;

    public JudgeEvalHarness(RagQueryUseCase ragQuery, Judge judge) {
        this.ragQuery = ragQuery;
        this.judge = judge;
    }

    public Report run(List<Case> cases, List<String> roles) {
        return Report.of(cases.stream().map(c -> score(c, roles)).toList());
    }

    private CaseResult score(Case c, List<String> roles) {
        Answer answer = ragQuery.answer(c.question(), roles);
        boolean refused = Refusal.isRefusal(answer.answer());
        Judge.Verdict verdict = judge.judge(c.question(), answer.answer(), answer.contexts());
        double correctness = refused || c.expectedGist().isBlank() ? 0.0 : tokenF1(answer.answer(), c.expectedGist());
        return new CaseResult(c, answer.answer(), verdict, refused, correctness);
    }

    static double tokenF1(String answer, String gist) {
        Set<String> a = TextAnalyzer.terms(answer);
        Set<String> g = TextAnalyzer.terms(gist);
        long common = a.stream().filter(g::contains).count();
        if (common == 0) {
            return 0.0;
        }
        double precision = (double) common / a.size();
        double recall = (double) common / g.size();
        return 2 * precision * recall / (precision + recall);
    }

    /** A golden question; {@code expectedGist} is the reference answer, an unanswerable case expects a refusal. */
    public record Case(String question, String expectedGist, boolean answerable) {
        public Case {
            expectedGist = expectedGist == null ? "" : expectedGist;
        }

        public Case(String question, String expectedGist) {
            this(question, expectedGist, true);
        }

        public Case(String question) {
            this(question, "", true);
        }

        public static Case unanswerable(String question) {
            return new Case(question, "", false);
        }
    }

    public record CaseResult(Case query, String answer, Judge.Verdict verdict, boolean refused, double correctness) {
    }

    /** Grounding means cover answered (non-refused) answerable cases; refusal metrics split the two populations. */
    public record Report(List<CaseResult> results, double meanFaithfulness, double meanAnswerRelevance,
                         double meanCorrectness, double refusalAccuracy, double falseRefusalRate) {

        static Report of(List<CaseResult> results) {
            List<CaseResult> answerable = results.stream().filter(r -> r.query().answerable()).toList();
            List<CaseResult> answered = answerable.stream().filter(r -> !r.refused()).toList();
            List<CaseResult> withGist = answerable.stream().filter(r -> !r.query().expectedGist().isBlank()).toList();
            List<CaseResult> unanswerable = results.stream().filter(r -> !r.query().answerable()).toList();
            return new Report(results,
                    mean(answered, r -> r.verdict().faithfulness()),
                    mean(answered, r -> r.verdict().answerRelevance()),
                    mean(withGist, CaseResult::correctness),
                    mean(unanswerable, r -> r.refused() ? 1 : 0),
                    mean(answerable, r -> r.refused() ? 1 : 0));
        }

        public double meanOverall() {
            return (meanFaithfulness + meanAnswerRelevance) / 2.0;
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("| question | answerable | refused | faithfulness | answerRelevance | correctness |\n");
            sb.append("|---|---|---|---:|---:|---:|\n");
            for (CaseResult r : results) {
                sb.append("| %s | %s | %s | %.2f | %.2f | %.2f |%n".formatted(r.query().question(),
                        r.query().answerable(), r.refused(), r.verdict().faithfulness(),
                        r.verdict().answerRelevance(), r.correctness()));
            }
            sb.append("| **mean** |  |  | **%.2f** | **%.2f** | **%.2f** |%n"
                    .formatted(meanFaithfulness, meanAnswerRelevance, meanCorrectness));
            sb.append("%nrefusal accuracy (unanswerable) %.2f | false refusal rate (answerable) %.2f%n"
                    .formatted(refusalAccuracy, falseRefusalRate));
            return sb.toString();
        }
    }

    private static double mean(List<CaseResult> results, ToDoubleFunction<CaseResult> metric) {
        return results.stream().mapToDouble(metric).average().orElse(0);
    }
}
