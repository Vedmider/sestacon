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
}
