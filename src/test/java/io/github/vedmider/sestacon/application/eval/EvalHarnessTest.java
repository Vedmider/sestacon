package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.application.eval.EvalHarness.Case;
import io.github.vedmider.sestacon.application.eval.EvalHarness.Report;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EvalHarnessTest {

    /** Fake retriever returning a scripted hit list per query. */
    private static RetrieveUseCase retriever(Map<String, List<RetrievedChunk>> scripted) {
        return (query, topK, roles) -> scripted.getOrDefault(query, List.of()).stream()
                .limit(topK).toList();
    }

    private static RetrievedChunk hit(String source) {
        return new RetrievedChunk("text", source, 1.0);
    }

    @Test
    void perfectRankingScoresFullPrecisionAndMrr() {
        var harness = new EvalHarness(retriever(Map.of(
                "q1", List.of(hit("doc-a")),
                "q2", List.of(hit("doc-b")))));

        Report r = harness.run(List.of(new Case("q1", "doc-a"), new Case("q2", "doc-b")), 5, List.of());

        assertThat(r.meanPrecisionAtK()).isCloseTo(1.0, within(1e-9));
        assertThat(r.mrr()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void relevantResultLowerDownReducesMrrButNotRecall() {
        // relevant hit is at rank 3 of 3 -> RR = 1/3, precision = 1/3
        var harness = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("noise-1"), hit("noise-2"), hit("doc-target")))));

        Report r = harness.run(List.of(new Case("q", "doc-target")), 5, List.of());

        assertThat(r.mrr()).isCloseTo(1.0 / 3.0, within(1e-9));
        assertThat(r.meanPrecisionAtK()).isCloseTo(1.0 / 3.0, within(1e-9));
    }

    @Test
    void missingRelevantResultScoresZero() {
        var harness = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("noise-1"), hit("noise-2")))));

        Report r = harness.run(List.of(new Case("q", "doc-target")), 5, List.of());

        assertThat(r.mrr()).isZero();
        assertThat(r.meanPrecisionAtK()).isZero();
    }

    @Test
    void rerankingToTopWouldRaiseMrr_demonstratesTheGate() {
        // Before: relevant at rank 3 (RR 1/3). After a rerank that lifts it to rank 1 (RR 1.0).
        var before = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("n1"), hit("n2"), hit("gold")))));
        var after = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("gold"), hit("n1"), hit("n2")))));

        double mrrBefore = before.run(List.of(new Case("q", "gold")), 5, List.of()).mrr();
        double mrrAfter = after.run(List.of(new Case("q", "gold")), 5, List.of()).mrr();

        assertThat(mrrAfter).isGreaterThan(mrrBefore);
    }

    @Test
    void reportMarkdownHasRowsAndMean() {
        var harness = new EvalHarness(retriever(Map.of("q", List.of(hit("doc-a")))));
        String md = harness.run(List.of(new Case("q", "doc-a")), 5, List.of()).toMarkdown();
        assertThat(md).contains("P@k", "R@k", "mean");
    }

    @Test
    void recallCountsDistinctRelevantSourcesFound() {
        // two sources relevant; only one retrieved -> recall 1/2, but that one is at rank 1
        var harness = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("doc-a"), hit("noise")))));

        Report r = harness.run(
                List.of(new Case("q", Set.of("doc-a", "doc-b"))), 5, List.of());

        assertThat(r.meanRecallAtK()).isCloseTo(0.5, within(1e-9));
        assertThat(r.mrr()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void recallIsFullWhenAllRelevantSourcesRetrieved() {
        var harness = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("doc-a"), hit("doc-b")))));

        Report r = harness.run(
                List.of(new Case("q", Set.of("doc-a", "doc-b"))), 5, List.of());

        assertThat(r.meanRecallAtK()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void duplicateRelevantHitsDoNotInflateRecallBeyondOne() {
        var harness = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("doc-a"), hit("doc-a"), hit("doc-a")))));

        Report r = harness.run(List.of(new Case("q", Set.of("doc-a"))), 5, List.of());

        assertThat(r.meanRecallAtK()).isCloseTo(1.0, within(1e-9));
        assertThat(r.meanPrecisionAtK()).isCloseTo(1.0, within(1e-9));   // 3 relevant / 3 hits
    }

    @Test
    void missingRelevantSourceScoresZeroRecall() {
        var harness = new EvalHarness(retriever(Map.of(
                "q", List.of(hit("noise-1"), hit("noise-2")))));

        Report r = harness.run(List.of(new Case("q", Set.of("doc-target"))), 5, List.of());

        assertThat(r.meanRecallAtK()).isZero();
    }
}
