package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.application.eval.JudgeEvalHarness.Case;
import io.github.vedmider.sestacon.application.eval.JudgeEvalHarness.Report;
import io.github.vedmider.sestacon.domain.model.Answer;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RagQueryUseCase;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import io.github.vedmider.sestacon.domain.port.out.Judge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JudgeEvalHarnessTest {

    private static RetrieveUseCase retriever(Map<String, List<RetrievedChunk>> ctx) {
        return (q, topK, roles) -> ctx.getOrDefault(q, List.of()).stream().limit(topK).toList();
    }

    private static RagQueryUseCase answerer(Map<String, String> answers) {
        return (q, roles) -> new Answer(answers.getOrDefault(q, ""), List.of(), false);
    }

    private static RetrievedChunk chunk(String text) {
        return new RetrievedChunk(text, "doc", 1.0);
    }

    @Test
    void groundedAndRelevantAnswerScoresHigh() {
        String q = "what capital france";
        var retrieve = retriever(Map.of(q, List.of(chunk("paris is the capital of france"))));
        var rag = answerer(Map.of(q, "the capital france paris"));
        var harness = new JudgeEvalHarness(rag, retrieve, lexicalJudge());

        Report r = harness.run(List.of(new Case(q)), 5, List.of());

        assertThat(r.meanFaithfulness()).isGreaterThan(0.9);     // every answer term is in context
        assertThat(r.meanAnswerRelevance()).isGreaterThan(0.6);  // covers capital+france of the question
    }

    @Test
    void hallucinatedAnswerScoresLowFaithfulness() {
        String q = "what capital france";
        var retrieve = retriever(Map.of(q, List.of(chunk("paris is the capital of france"))));
        var rag = answerer(Map.of(q, "moon green cheese orbit crater"));
        var harness = new JudgeEvalHarness(rag, retrieve, lexicalJudge());

        Report r = harness.run(List.of(new Case(q)), 5, List.of());

        assertThat(r.meanFaithfulness()).isZero();   // no answer term appears in the context
    }

    @Test
    void offTopicAnswerScoresLowRelevance() {
        String q = "french rate limiting policy";
        var retrieve = retriever(Map.of(q, List.of(chunk("berlin munich hamburg cologne cities"))));
        var rag = answerer(Map.of(q, "berlin munich hamburg cologne"));
        var harness = new JudgeEvalHarness(rag, retrieve, lexicalJudge());

        Report r = harness.run(List.of(new Case(q)), 5, List.of());

        assertThat(r.meanAnswerRelevance()).isZero();   // answer covers none of the question terms
    }

    @Test
    void reportMarkdownHasMeans() {
        String q = "q question";
        var harness = new JudgeEvalHarness(
                answerer(Map.of(q, "x")), retriever(Map.of()), lexicalJudge());
        String md = harness.run(List.of(new Case(q)), 5, List.of()).toMarkdown();
        assertThat(md).contains("faithfulness", "answerRelevance", "mean");
    }

    @Test
    void overallAveragesTheTwoDimensions() {
        var v = new Judge.Verdict(0.8, 0.4, "x");
        assertThat(v.overall()).isCloseTo(0.6, within(1e-9));
    }

    /** The production default judge, exercised directly (no Spring). */
    private static Judge lexicalJudge() {
        return new io.github.vedmider.sestacon.adapter.out.judge.JudgeConfig().lexicalJudge();
    }
}
