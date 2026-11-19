package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.application.eval.EvalHarness.Case;
import io.github.vedmider.sestacon.application.eval.EvalHarness.CaseResult;
import io.github.vedmider.sestacon.application.eval.EvalHarness.Report;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;

import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;

/** Paired A/B over identical cases: per-case deltas, win/loss/tie and a seeded bootstrap 95% CI per metric. */
public final class AbComparison {

    static final int BOOTSTRAP_RESAMPLES = 2000;
    private static final long SEED = 42L;

    private AbComparison() {
    }

    public static Result run(String labelA, RetrieveUseCase a,
                             String labelB, RetrieveUseCase b,
                             List<Case> cases, int topK, List<String> roles) {
        Report ra = new EvalHarness(a).run(cases, topK, roles);
        Report rb = new EvalHarness(b).run(cases, topK, roles);
        return new Result(labelA, ra, labelB, rb);
    }

    /** Lower/upper 2.5% and 97.5% bootstrap quantiles of the mean of {@code deltas}; [0,0] when empty. */
    static double[] bootstrapCi95(double[] deltas) {
        if (deltas.length == 0) {
            return new double[]{0, 0};
        }
        SplittableRandom rnd = new SplittableRandom(SEED);
        double[] means = new double[BOOTSTRAP_RESAMPLES];
        for (int i = 0; i < BOOTSTRAP_RESAMPLES; i++) {
            double sum = 0;
            for (int j = 0; j < deltas.length; j++) {
                sum += deltas[rnd.nextInt(deltas.length)];
            }
            means[i] = sum / deltas.length;
        }
        Arrays.sort(means);
        return new double[]{EvalHarness.percentile(means, 2.5), EvalHarness.percentile(means, 97.5)};
    }

    public record Result(String labelA, Report a, String labelB, Report b) {

        private double[] deltas(ToDoubleFunction<CaseResult> metric, boolean negatives) {
            List<CaseResult> ra = a.results();
            List<CaseResult> rb = b.results();
            double[] out = new double[ra.size()];
            int n = 0;
            for (int i = 0; i < ra.size(); i++) {
                if (ra.get(i).query().isNegative() == negatives) {
                    out[n++] = metric.applyAsDouble(rb.get(i)) - metric.applyAsDouble(ra.get(i));
                }
            }
            return Arrays.copyOf(out, n);
        }

        private double[] rrDeltas() {
            return deltas(CaseResult::reciprocalRank, false);
        }

        public double precisionDelta() {
            return b.meanPrecisionAtK() - a.meanPrecisionAtK();
        }

        public double recallDelta() {
            return b.meanRecallAtK() - a.meanRecallAtK();
        }

        public double mrrDelta() {
            return b.mrr() - a.mrr();
        }

        public double abstentionDelta() {
            return b.abstentionRate() - a.abstentionRate();
        }

        public int wins() {
            return (int) Arrays.stream(rrDeltas()).filter(d -> d > 1e-12).count();
        }

        public int losses() {
            return (int) Arrays.stream(rrDeltas()).filter(d -> d < -1e-12).count();
        }

        public int ties() {
            return rrDeltas().length - wins() - losses();
        }

        public double mrrDeltaCi95Low() {
            return bootstrapCi95(rrDeltas())[0];
        }

        public double mrrDeltaCi95High() {
            return bootstrapCi95(rrDeltas())[1];
        }

        public double recallDeltaCi95Low() {
            return bootstrapCi95(deltas(CaseResult::recallAtK, false))[0];
        }

        public double abstentionDeltaCi95Low() {
            return bootstrapCi95(deltas(r -> r.abstained() ? 1 : 0, true))[0];
        }

        /** B is within {@code margin} of A on MRR, recall and abstention at the 95% CI lower bound. */
        public boolean isNonInferior(double margin) {
            return mrrDeltaCi95Low() >= -margin && recallDeltaCi95Low() >= -margin
                    && abstentionDeltaCi95Low() >= -margin;
        }

        public boolean bIsNoWorse() {
            return isNonInferior(0.0);
        }

        public boolean bIsSignificantlyBetter() {
            return mrrDeltaCi95Low() > 0;
        }

        public String toMarkdown() {
            return """
                    | metric | %s | %s | delta | 95%% CI |
                    |---|---:|---:|---:|---|
                    | P@k | %.3f | %.3f | %+.3f | |
                    | R@k | %.3f | %.3f | %+.3f | [%+.3f, %+.3f] |
                    | MRR | %.3f | %.3f | %+.3f | [%+.3f, %+.3f] |
                    | hit rate | %.3f | %.3f | %+.3f | |
                    | abstention | %.3f | %.3f | %+.3f | |

                    win/loss/tie (per-case RR): %d/%d/%d | no-worse: %s | significantly better: %s
                    """.formatted(
                    labelA, labelB,
                    a.meanPrecisionAtK(), b.meanPrecisionAtK(), precisionDelta(),
                    a.meanRecallAtK(), b.meanRecallAtK(), recallDelta(),
                    recallDeltaCi95Low(), bootstrapCi95(deltas(CaseResult::recallAtK, false))[1],
                    a.mrr(), b.mrr(), mrrDelta(), mrrDeltaCi95Low(), mrrDeltaCi95High(),
                    a.hitRate(), b.hitRate(), b.hitRate() - a.hitRate(),
                    a.abstentionRate(), b.abstentionRate(), abstentionDelta(),
                    wins(), losses(), ties(), bIsNoWorse(), bIsSignificantlyBetter());
        }
    }
}
