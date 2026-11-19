package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.application.eval.EvalHarness.Case;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AbComparisonTest {

    private static RetrieveUseCase arm(Map<String, List<RetrievedChunk>> scripted) {
        return (q, topK, roles) -> scripted.getOrDefault(q, List.of()).stream().limit(topK).toList();
    }

    private static RetrievedChunk hit(String source) {
        return new RetrievedChunk("text", source, 1.0);
    }

    @Test
    void bRankingGoldHigherRaisesMrrAndIsNoWorse() {
        // A: gold at rank 3 (RR 1/3). B: gold at rank 1 (RR 1.0) — e.g. after a reranker.
        var a = arm(Map.of("q", List.of(hit("n1"), hit("n2"), hit("gold"))));
        var b = arm(Map.of("q", List.of(hit("gold"), hit("n1"), hit("n2"))));
        var cases = List.of(new Case("q", "gold"));

        var r = AbComparison.run("baseline", a, "reranked", b, cases, 5, List.of());

        assertThat(r.mrrDelta()).isCloseTo(1.0 - 1.0 / 3.0, within(1e-9));
        assertThat(r.recallDelta()).isZero();          // both retrieve gold
        assertThat(r.bIsNoWorse()).isTrue();
    }

    @Test
    void bMissingGoldIsAregressionAndFlagged() {
        var a = arm(Map.of("q", List.of(hit("gold"))));
        var b = arm(Map.of("q", List.of(hit("noise"))));
        var cases = List.of(new Case("q", "gold"));

        var r = AbComparison.run("baseline", a, "broken", b, cases, 5, List.of());

        assertThat(r.recallDelta()).isLessThan(0);
        assertThat(r.mrrDelta()).isLessThan(0);
        assertThat(r.bIsNoWorse()).isFalse();
    }

    @Test
    void reportMarkdownShowsBothArmsAndDeltas() {
        var a = arm(Map.of("q", List.of(hit("gold"))));
        var b = arm(Map.of("q", List.of(hit("gold"))));
        String md = AbComparison.run("A", a, "B", b, List.of(new Case("q", "gold")), 5, List.of())
                .toMarkdown();
        assertThat(md).contains("P@k", "R@k", "MRR", "delta", "A", "B");
    }

    @Test
    void pairedComparisonCountsWinsLossesAndTiesPerCase() {
        var a = arm(Map.of("q1", List.of(hit("n"), hit("g1")), "q2", List.of(hit("g2")), "q3", List.of(hit("g3"))));
        var b = arm(Map.of("q1", List.of(hit("g1")), "q2", List.of(hit("n"), hit("g2")), "q3", List.of(hit("g3"))));
        var cases = List.of(new Case("q1", "g1"), new Case("q2", "g2"), new Case("q3", "g3"));

        var r = AbComparison.run("A", a, "B", b, cases, 5, List.of());

        assertThat(r.wins()).isEqualTo(1);
        assertThat(r.losses()).isEqualTo(1);
        assertThat(r.ties()).isEqualTo(1);
    }

    @Test
    void aMeanGainHidingARegressionIsNotNoWorse() {
        // B wins big on two cases but loses one: the naked mean delta is positive, the paired CI is not >= 0
        var a = arm(Map.of("q1", List.of(hit("n"), hit("n"), hit("g1")), "q2", List.of(hit("n"), hit("n"), hit("g2")),
                "q3", List.of(hit("g3"))));
        var b = arm(Map.of("q1", List.of(hit("g1")), "q2", List.of(hit("g2")), "q3", List.of(hit("n"))));
        var cases = List.of(new Case("q1", "g1"), new Case("q2", "g2"), new Case("q3", "g3"));

        var r = AbComparison.run("A", a, "B", b, cases, 5, List.of());

        assertThat(r.mrrDelta()).isPositive();
        assertThat(r.mrrDeltaCi95Low()).isNegative();
        assertThat(r.bIsNoWorse()).isFalse();
        assertThat(r.isNonInferior(1.0)).isTrue();
    }

    @Test
    void bootstrapIntervalIsDeterministicAndBracketsTheMean() {
        var a = arm(Map.of("q1", List.of(hit("n"), hit("g1")), "q2", List.of(hit("g2"))));
        var b = arm(Map.of("q1", List.of(hit("g1")), "q2", List.of(hit("g2"))));
        var cases = List.of(new Case("q1", "g1"), new Case("q2", "g2"));

        var r1 = AbComparison.run("A", a, "B", b, cases, 5, List.of());
        var r2 = AbComparison.run("A", a, "B", b, cases, 5, List.of());

        assertThat(r1.mrrDeltaCi95Low()).isEqualTo(r2.mrrDeltaCi95Low());
        assertThat(r1.mrrDeltaCi95Low()).isLessThanOrEqualTo(r1.mrrDelta());
        assertThat(r1.mrrDeltaCi95High()).isGreaterThanOrEqualTo(r1.mrrDelta());
        assertThat(r1.toMarkdown()).contains("95% CI", "win/loss/tie");
    }
}
