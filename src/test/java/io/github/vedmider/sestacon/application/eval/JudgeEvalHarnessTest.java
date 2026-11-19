package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.application.eval.JudgeEvalHarness.Case;
import io.github.vedmider.sestacon.application.eval.JudgeEvalHarness.Report;
import io.github.vedmider.sestacon.domain.model.Answer;
import io.github.vedmider.sestacon.domain.port.in.RagQueryUseCase;
import io.github.vedmider.sestacon.domain.port.out.Judge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JudgeEvalHarnessTest {

    private record Scripted(String answer, List<String> contexts) {
    }

    private static RagQueryUseCase answerer(Map<String, Scripted> answers) {
        return (q, roles) -> {
            Scripted s = answers.getOrDefault(q, new Scripted("", List.of()));
            return new Answer(s.answer(), List.of("doc"), false, !s.contexts().isEmpty(), s.contexts());
        };
    }

    private static JudgeEvalHarness harness(Map<String, Scripted> answers) {
        return new JudgeEvalHarness(answerer(answers), lexicalJudge());
    }

    @Test
    void groundedAndRelevantAnswerScoresHigh() {
        String q = "what capital france";
        Report r = harness(Map.of(q, new Scripted("the capital france paris", List.of("paris is the capital of france"))))
                .run(List.of(new Case(q)), List.of());

        assertThat(r.meanFaithfulness()).isGreaterThan(0.9);
        assertThat(r.meanAnswerRelevance()).isGreaterThan(0.6);
    }

    @Test
    void hallucinatedAnswerScoresLowFaithfulness() {
        String q = "what capital france";
        Report r = harness(Map.of(q, new Scripted("moon green cheese orbit crater", List.of("paris is the capital of france"))))
                .run(List.of(new Case(q)), List.of());

        assertThat(r.meanFaithfulness()).isZero();
    }

    @Test
    void offTopicAnswerScoresLowRelevance() {
        String q = "french rate limiting policy";
        Report r = harness(Map.of(q, new Scripted("berlin munich hamburg cologne", List.of("berlin munich hamburg cologne cities"))))
                .run(List.of(new Case(q)), List.of());

        assertThat(r.meanAnswerRelevance()).isZero();
    }

    @Test
    void faithfulnessIsJudgedAgainstTheContextsTheAnswerWasGroundedOn() {
        String q = "what capital france";
        Report r = harness(Map.of(q, new Scripted("paris", List.of())))
                .run(List.of(new Case(q)), List.of());

        assertThat(r.meanFaithfulness()).as("an answer built from no context is not grounded").isZero();
    }

    @Test
    void stopwordsDoNotCountAsGrounding() {
        String q = "what capital france";
        Report r = harness(Map.of(q, new Scripted("the and of the moon", List.of("the capital of france is paris"))))
                .run(List.of(new Case(q)), List.of());

        assertThat(r.meanFaithfulness()).isZero();
    }

    @Test
    void refusingAnUnanswerableQuestionIsCorrectAndExcludedFromGroundingMeans() {
        String unanswerable = "what is the parking policy";
        String answerable = "what capital france";
        Report r = harness(Map.of(
                unanswerable, new Scripted("I do not know.", List.of()),
                answerable, new Scripted("paris", List.of("paris is the capital of france"))))
                .run(List.of(Case.unanswerable(unanswerable), new Case(answerable, "paris")), List.of());

        assertThat(r.refusalAccuracy()).isCloseTo(1.0, within(1e-9));
        assertThat(r.falseRefusalRate()).isZero();
        assertThat(r.meanFaithfulness()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void refusingAnAnswerableQuestionIsAFalseRefusal() {
        String q = "what capital france";
        Report r = harness(Map.of(q, new Scripted("I don't know", List.of("paris is the capital of france"))))
                .run(List.of(new Case(q, "paris")), List.of());

        assertThat(r.falseRefusalRate()).isCloseTo(1.0, within(1e-9));
        assertThat(r.meanCorrectness()).isZero();
    }

    @Test
    void answeringAnUnanswerableQuestionIsNotACorrectRefusal() {
        String q = "what is the parking policy";
        Report r = harness(Map.of(q, new Scripted("parking is free on fridays", List.of("the team handbook"))))
                .run(List.of(Case.unanswerable(q)), List.of());

        assertThat(r.refusalAccuracy()).isZero();
    }

    @Test
    void correctnessIsTokenF1AgainstTheExpectedGist() {
        String q = "what capital france";
        Report r = harness(Map.of(q, new Scripted("the capital is paris", List.of("paris is the capital of france"))))
                .run(List.of(new Case(q, "paris")), List.of());

        assertThat(r.meanCorrectness()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void reportMarkdownHasMeans() {
        String md = harness(Map.of("q question", new Scripted("x", List.of())))
                .run(List.of(new Case("q question")), List.of()).toMarkdown();

        assertThat(md).contains("faithfulness", "answerRelevance", "correctness", "refusal", "mean");
    }

    @Test
    void overallAveragesTheTwoDimensions() {
        var v = new Judge.Verdict(0.8, 0.4, "x");
        assertThat(v.overall()).isCloseTo(0.6, within(1e-9));
    }

    private static Judge lexicalJudge() {
        return new io.github.vedmider.sestacon.adapter.out.judge.JudgeConfig().lexicalJudge();
    }
}
