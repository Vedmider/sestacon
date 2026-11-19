package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Offline retrieval eval: positives get context precision/recall/RR/hit rate, negatives get abstention; exact doc-id match. */
@Component
public class EvalHarness {

    public static final String DEFAULT_SLICE = "default";

    private final RetrieveUseCase retrieve;

    public EvalHarness(RetrieveUseCase retrieve) {
        this.retrieve = retrieve;
    }

    public Report run(List<Case> cases, int topK, List<String> roles) {
        return Report.of(cases.stream().map(c -> score(c, topK, roles)).toList());
    }

    private CaseResult score(Case c, int topK, List<String> roles) {
        long start = System.nanoTime();
        List<RetrievedChunk> hits = retrieve.retrieve(c.query(), topK, roles);
        double latencyMillis = (System.nanoTime() - start) / 1_000_000.0;
        int firstRelevantRank = 0;
        Set<String> found = new HashSet<>();
        int relevant = 0;
        for (int i = 0; i < hits.size(); i++) {
            String source = hits.get(i).source();
            if (source != null && c.relevantSources().contains(source)) {
                relevant++;
                found.add(source);
                if (firstRelevantRank == 0) {
                    firstRelevantRank = i + 1;
                }
            }
        }
        double precision = hits.isEmpty() ? 0.0 : (double) relevant / hits.size();
        double recall = c.isNegative() ? 0.0 : (double) found.size() / c.relevantSources().size();
        double rr = firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank;
        return new CaseResult(c, hits.size(), relevant, firstRelevantRank, precision, recall, rr, latencyMillis);
    }

    /** A labelled case; an empty {@code relevantSources} marks a negative case where no document answers it. */
    public record Case(String query, Set<String> relevantSources, String slice) {
        public Case {
            relevantSources = relevantSources == null ? Set.of() : Set.copyOf(relevantSources);
            slice = slice == null || slice.isBlank() ? DEFAULT_SLICE : slice;
        }

        public Case(String query, Set<String> relevantSources) {
            this(query, relevantSources, DEFAULT_SLICE);
        }

        public Case(String query, String expectedSource) {
            this(query, Set.of(expectedSource), DEFAULT_SLICE);
        }

        public static Case negative(String query, String slice) {
            return new Case(query, Set.of(), slice);
        }

        public boolean isNegative() {
            return relevantSources.isEmpty();
        }
    }

    public record CaseResult(Case query, int hits, int relevant, int firstRelevantRank,
                             double precisionAtK, double recallAtK, double reciprocalRank, double latencyMillis) {
        public boolean hit() {
            return firstRelevantRank > 0;
        }

        public boolean abstained() {
            return hits == 0;
        }
    }

    public record SliceStats(int positives, int negatives, double hitRate, double mrr, double meanRecallAtK,
                             double abstentionRate) {
        static SliceStats of(List<CaseResult> results) {
            List<CaseResult> pos = results.stream().filter(r -> !r.query().isNegative()).toList();
            List<CaseResult> neg = results.stream().filter(r -> r.query().isNegative()).toList();
            return new SliceStats(pos.size(), neg.size(),
                    mean(pos, r -> r.hit() ? 1 : 0), mean(pos, CaseResult::reciprocalRank),
                    mean(pos, CaseResult::recallAtK), mean(neg, r -> r.abstained() ? 1 : 0));
        }
    }

    /** Positive-case means exclude negatives; negatives contribute only to {@link #abstentionRate()}. */
    public record Report(List<CaseResult> results, double meanPrecisionAtK, double meanRecallAtK, double mrr,
                         double hitRate, int negatives, double abstentionRate,
                         double latencyP50Millis, double latencyP95Millis, Map<String, SliceStats> bySlice) {

        static Report of(List<CaseResult> results) {
            List<CaseResult> pos = results.stream().filter(r -> !r.query().isNegative()).toList();
            List<CaseResult> neg = results.stream().filter(r -> r.query().isNegative()).toList();
            double[] latencies = results.stream().mapToDouble(CaseResult::latencyMillis).sorted().toArray();
            Map<String, SliceStats> slices = results.stream()
                    .collect(Collectors.groupingBy(r -> r.query().slice(), TreeMap::new, Collectors.toList()))
                    .entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> SliceStats.of(e.getValue()),
                            (x, y) -> x, TreeMap::new));
            return new Report(results, mean(pos, CaseResult::precisionAtK), mean(pos, CaseResult::recallAtK),
                    mean(pos, CaseResult::reciprocalRank), mean(pos, r -> r.hit() ? 1 : 0),
                    neg.size(), mean(neg, r -> r.abstained() ? 1 : 0),
                    percentile(latencies, 50), percentile(latencies, 95), slices);
        }

        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("| query | slice | expected | hits | rel | firstRank | P@k | R@k | RR |\n");
            sb.append("|---|---|---|---:|---:|---:|---:|---:|---:|\n");
            for (CaseResult r : results) {
                sb.append("| %s | %s | %s | %d | %d | %d | %.2f | %.2f | %.2f |%n".formatted(
                        r.query().query(), r.query().slice(),
                        r.query().isNegative() ? "(none)" : r.query().relevantSources(), r.hits(), r.relevant(),
                        r.firstRelevantRank(), r.precisionAtK(), r.recallAtK(), r.reciprocalRank()));
            }
            sb.append("| **mean (positives)** |  |  |  |  |  | **%.2f** | **%.2f** | **%.2f** |%n"
                    .formatted(meanPrecisionAtK, meanRecallAtK, mrr));
            sb.append("%nhit rate %.2f | abstention %.2f over %d negatives | latency p50 %.1f ms p95 %.1f ms%n"
                    .formatted(hitRate, abstentionRate, negatives, latencyP50Millis, latencyP95Millis));
            sb.append("%n| slice | pos | neg | hit rate | MRR | R@k | abstention |%n|---|---:|---:|---:|---:|---:|---:|%n"
                    .formatted());
            bySlice.forEach((name, s) -> sb.append("| %s | %d | %d | %.2f | %.2f | %.2f | %.2f |%n".formatted(
                    name, s.positives(), s.negatives(), s.hitRate(), s.mrr(), s.meanRecallAtK(), s.abstentionRate())));
            return sb.toString();
        }
    }

    static double mean(List<CaseResult> results, ToDoubleFunction<CaseResult> metric) {
        return results.stream().mapToDouble(metric).average().orElse(0);
    }

    static double percentile(double[] sorted, double p) {
        if (sorted.length == 0) {
            return 0;
        }
        double[] s = sorted.clone();
        Arrays.sort(s);
        int idx = (int) Math.ceil(p / 100.0 * s.length) - 1;
        return s[Math.max(0, Math.min(s.length - 1, idx))];
    }
}
