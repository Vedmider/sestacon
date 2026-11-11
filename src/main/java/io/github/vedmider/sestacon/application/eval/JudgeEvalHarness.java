package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.domain.model.Answer;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RagQueryUseCase;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import io.github.vedmider.sestacon.domain.port.out.Judge;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Answer-quality gate (LLM-as-judge). For each golden question it retrieves the contexts, generates
 * an answer through the real RAG path, and scores faithfulness + answer-relevance with a {@link Judge}.
 * This measures generation quality, which the retrieval-only {@link EvalHarness} does not.
 */
@Component
public class JudgeEvalHarness {

    private final RagQueryUseCase ragQuery;
    private final RetrieveUseCase retrieve;
    private final Judge judge;

    public JudgeEvalHarness(RagQueryUseCase ragQuery, RetrieveUseCase retrieve, Judge judge) {
        this.ragQuery = ragQuery;
        this.retrieve = retrieve;
        this.judge = judge;
    }

    public Report run(List<Case> cases, int topK, List<String> roles) {
        List<CaseResult> results = cases.stream().map(c -> score(c, topK, roles)).toList();
        double f = results.stream().mapToDouble(r -> r.verdict().faithfulness()).average().orElse(0);
        double a = results.stream().mapToDouble(r -> r.verdict().answerRelevance()).average().orElse(0);
        return new Report(results, f, a);
    }

    private CaseResult score(Case c, int topK, List<String> roles) {
        List<String> contexts = retrieve.retrieve(c.question(), topK, roles).stream()
                .map(RetrievedChunk::text).toList();
        Answer answer = ragQuery.answer(c.question(), roles);
        Judge.Verdict verdict = judge.judge(c.question(), answer.answer(), contexts);
        return new CaseResult(c, answer.answer(), verdict);
    }

    /** A golden question. {@code expectedGist} documents intent; scoring is judge-driven, not string-matched. */
    public record Case(String question, String expectedGist) {
        public Case(String question) {
            this(question, "");
        }
    }

    public record CaseResult(Case query, String answer, Judge.Verdict verdict) {
    }

    public record Report(List<CaseResult> results, double meanFaithfulness, double meanAnswerRelevance) {
        public double meanOverall() {
            return (meanFaithfulness + meanAnswerRelevance) / 2.0;
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("| question | faithfulness | answerRelevance |\n");
            sb.append("|---|---:|---:|\n");
            for (CaseResult r : results) {
                sb.append("| %s | %.2f | %.2f |%n".formatted(
                        r.query().question(), r.verdict().faithfulness(), r.verdict().answerRelevance()));
            }
            sb.append("| **mean** | **%.2f** | **%.2f** |%n"
                    .formatted(meanFaithfulness, meanAnswerRelevance));
            return sb.toString();
        }
    }
}
