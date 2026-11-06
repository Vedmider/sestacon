package com.example.rag.application.eval;

import com.example.rag.domain.model.RetrievedChunk;
import com.example.rag.domain.port.in.RetrieveUseCase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Retrieval-quality gate. Replays labelled cases through {@link RetrieveUseCase} and scores
 * precision@k and MRR by whether a retrieved chunk's source matches the case's expected source.
 * This makes ranking changes (e.g. a reranker) measurable rather than asserted.
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
        double mrr = results.stream().mapToDouble(CaseResult::reciprocalRank).average().orElse(0);
        return new Report(results, p, mrr);
    }

    private CaseResult score(Case c, int topK, List<String> roles) {
        List<RetrievedChunk> hits = retrieve.retrieve(c.query(), topK, roles);
        int firstRelevantRank = 0;          // 1-based; 0 = not found
        int relevant = 0;
        for (int i = 0; i < hits.size(); i++) {
            if (matches(hits.get(i), c.expectedSource())) {
                relevant++;
                if (firstRelevantRank == 0) {
                    firstRelevantRank = i + 1;
                }
            }
        }
        double precision = hits.isEmpty() ? 0.0 : (double) relevant / hits.size();
        double rr = firstRelevantRank == 0 ? 0.0 : 1.0 / firstRelevantRank;
        return new CaseResult(c, hits.size(), relevant, firstRelevantRank, precision, rr);
    }

    private static boolean matches(RetrievedChunk hit, String expectedSource) {
        return hit.source() != null && hit.source().contains(expectedSource);
    }

    /** A labelled eval case: a query and a source substring a relevant chunk must carry. */
    public record Case(String query, String expectedSource) {
    }

    public record CaseResult(Case query, int hits, int relevant, int firstRelevantRank,
                             double precisionAtK, double reciprocalRank) {
    }

    public record Report(List<CaseResult> results, double meanPrecisionAtK, double mrr) {
        public String toMarkdown() {
            StringBuilder sb = new StringBuilder();
            sb.append("| query | expected | hits | rel | firstRank | P@k | RR |\n");
            sb.append("|---|---|---:|---:|---:|---:|---:|\n");
            for (CaseResult r : results) {
                sb.append("| %s | %s | %d | %d | %d | %.2f | %.2f |%n".formatted(
                        r.query().query(), r.query().expectedSource(), r.hits(), r.relevant(),
                        r.firstRelevantRank(), r.precisionAtK(), r.reciprocalRank()));
            }
            sb.append("| **mean** |  |  |  |  | **%.2f** | **%.2f** |%n"
                    .formatted(meanPrecisionAtK, mrr));
            return sb.toString();
        }
    }
}
