package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.application.eval.EvalHarness.Case;
import io.github.vedmider.sestacon.application.eval.EvalHarness.Report;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;

import java.util.List;

/**
 * A/B evaluation of two retrieval strategies over one labelled set. Scores each arm with
 * {@link EvalHarness} and reports the deltas, so a change (e.g. enabling a reranker) is
 * accepted or rejected on measured P@k / recall / MRR rather than on belief.
 */
public final class AbComparison {

    private AbComparison() {
    }

    public static Result run(String labelA, RetrieveUseCase a,
                             String labelB, RetrieveUseCase b,
                             List<Case> cases, int topK, List<String> roles) {
        Report ra = new EvalHarness(a).run(cases, topK, roles);
        Report rb = new EvalHarness(b).run(cases, topK, roles);
        return new Result(labelA, ra, labelB, rb);
    }

    public record Result(String labelA, Report a, String labelB, Report b) {
        public double precisionDelta() {
            return b.meanPrecisionAtK() - a.meanPrecisionAtK();
        }

        public double recallDelta() {
            return b.meanRecallAtK() - a.meanRecallAtK();
        }

        public double mrrDelta() {
            return b.mrr() - a.mrr();
        }

        /** True if B is at least as good as A on every metric (no regression). */
        public boolean bIsNoWorse() {
            return precisionDelta() >= 0 && recallDelta() >= 0 && mrrDelta() >= 0;
        }

        public String toMarkdown() {
            return """
                    | metric | %s | %s | delta |
                    |---|---:|---:|---:|
                    | P@k | %.3f | %.3f | %+.3f |
                    | R@k | %.3f | %.3f | %+.3f |
                    | MRR | %.3f | %.3f | %+.3f |
                    """.formatted(
                    labelA, labelB,
                    a.meanPrecisionAtK(), b.meanPrecisionAtK(), precisionDelta(),
                    a.meanRecallAtK(), b.meanRecallAtK(), recallDelta(),
                    a.mrr(), b.mrr(), mrrDelta());
        }
    }
}
