package io.github.vedmider.sestacon.application.eval;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Retrieval-quality gate. Replays labelled cases through {@link RetrieveUseCase} and scores
 * precision@k, recall@k and MRR by whether a retrieved chunk's source matches the case's
 * relevant sources. This makes ranking changes (e.g. a reranker) measurable rather than asserted.
 */
@Component
public class EvalHarness {

    private final RetrieveUseCase retrieve;

    public EvalHarness(RetrieveUseCase retrieve) {
        this.retrieve = retrieve;
    }

    public Report run(List<Case> cases, int topK, List<String> roles) {
        List<CaseResult> results = cases.stream().map(c -> score(c, topK, roles)).toList();
        double p = results.stream().mapToDouble(CaseResult::precisionAtK).average().orElse(0);
        double recall = results.stream().mapToDouble(CaseResult::recallAtK).average().orElse(0);
        double mrr = results.stream().mapToDouble(CaseResult::reciprocalRank).average().orElse(0);
        return new Report(results, p, recall, mrr);
    }

    private CaseResult score(Case c, int topK, List<String> roles) {
        List<RetrievedChunk> hits = retrieve.retrieve(c.query(), topK, roles);
        int firstRelevantRank = 0;          // 1-based; 0 = not found
        Set<String> found = new java.util.HashSet<>();
        int relevant = 0;
        for (int i = 0; i < hits.size(); i++) {
            String match = matchedSource(hits.get(i), c.relevantSources());
            if (match != null) {
                relevant++;
                found.add(match);
                if (firstRelevantRank == 0) {
                    firstRelevantRank = i + 1;
                }
            }
        }
        double precision = hits.isEmpty() ? 0.0 : (double) relevant / hits.size();
        double recall = c.relevantSources().isEmpty()
                ? 0.0 : (double) found.size() / c.relevantSources().size();
        double rr = firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank;
        return new CaseResult(c, hits.size(), relevant, firstRelevantRank, precision, recall, rr);
    }

    private static String matchedSource(RetrievedChunk hit, Set<String> relevantSources) {
        if (hit.source() == null) {
            return null;
        }
        return relevantSources.stream().filter(s -> hit.source().contains(s)).findFirst().orElse(null);
    }

    /** A labelled eval case: a query and the source substrings a relevant chunk may carry. */
    public record Case(String query, Set<String> relevantSources) {
        public Case(String query, String expectedSource) {
            this(query, Set.of(expectedSource));
        }
    }

    public record CaseResult(Case query, int hits, int relevant, int firstRelevantRank,
                             double precisionAtK, double recallAtK, double reciprocalRank) {
    }

    public record Report(List<CaseResult> results, double meanPrecisionAtK,
                         double meanRecallAtK, double mrr) {
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("| query | expected | hits | rel | firstRank | P@k | R@k | RR |\n");
            sb.append("|---|---|---:|---:|---:|---:|---:|---:|\n");
            for (CaseResult r : results) {
                sb.append("| %s | %s | %d | %d | %d | %.2f | %.2f | %.2f |%n".formatted(
                        r.query().query(), r.query().relevantSources(), r.hits(), r.relevant(),
                        r.firstRelevantRank(), r.precisionAtK(), r.recallAtK(), r.reciprocalRank()));
            }
            sb.append("| **mean** |  |  |  |  | **%.2f** | **%.2f** | **%.2f** |%n"
                    .formatted(meanPrecisionAtK, meanRecallAtK, mrr));
            return sb.toString();
        }
    }
}
