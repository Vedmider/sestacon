package io.github.vedmider.sestacon.adapter.out.rerank;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.model.TextAnalyzer;
import io.github.vedmider.sestacon.domain.port.out.Reranker;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** Relevance = max(trusted vector similarity, IDF-weighted query-term coverage if it reaches a majority of the query). */
public class LexicalReranker implements Reranker {

    public static final double DEFAULT_MIN_LEXICAL_COVERAGE = 0.6;

    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}+");
    private static final int MIN_STEM = 4;
    private static final int MAX_SUFFIX = 2;

    private final double minLexicalCoverage;
    private final boolean vectorTrustedForNonLatin;

    public LexicalReranker() {
        this(DEFAULT_MIN_LEXICAL_COVERAGE, false);
    }

    public LexicalReranker(double minLexicalCoverage, boolean vectorTrustedForNonLatin) {
        this.minLexicalCoverage = minLexicalCoverage;
        this.vectorTrustedForNonLatin = vectorTrustedForNonLatin;
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        Set<String> queryTerms = TextAnalyzer.terms(query);
        List<Set<String>> chunkTerms = candidates.stream().map(c -> TextAnalyzer.terms(c.text())).toList();
        Map<String, Double> weights = idf(queryTerms, chunkTerms);
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        boolean trustVector = vectorTrustedForNonLatin || mostlyLatin(query);

        return IntStream.range(0, candidates.size())
                .mapToObj(i -> {
                    RetrievedChunk c = candidates.get(i);
                    double sim = Math.max(0.0, Math.min(1.0, c.score()));
                    double lexical = total == 0 ? 0.0 : queryTerms.stream()
                            .filter(t -> matches(chunkTerms.get(i), t)).mapToDouble(weights::get).sum() / total;
                    double lexicalEvidence = lexical >= minLexicalCoverage ? lexical : 0.0;
                    return c.withSignals(Math.max(trustVector ? sim : 0.0, lexicalEvidence), sim, lexical);
                })
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(Math.max(topK, 0))
                .toList();
    }

    private static Map<String, Double> idf(Set<String> queryTerms, List<Set<String>> chunkTerms) {
        int n = chunkTerms.size();
        Map<String, Double> weights = new HashMap<>();
        for (String t : queryTerms) {
            long df = chunkTerms.stream().filter(s -> matches(s, t)).count();
            weights.put(t, Math.log(1.0 + (n + 1.0) / (df + 0.5)));
        }
        return weights;
    }

    // Cyrillic words inflect by suffix (подати/подають); English plurals are already folded by TextAnalyzer.
    static boolean matches(Set<String> chunkTerms, String term) {
        if (chunkTerms.contains(term)) {
            return true;
        }
        if (!CYRILLIC.matcher(term).matches()) {
            return false;
        }
        for (String c : chunkTerms) {
            int need = Math.max(MIN_STEM, Math.min(term.length(), c.length()) - MAX_SUFFIX);
            if (commonPrefix(term, c) >= need) {
                return true;
            }
        }
        return false;
    }

    private static int commonPrefix(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    // The default embedding (all-MiniLM-L6-v2) is English-only: its similarity for other scripts is noise.
    private static boolean mostlyLatin(String query) {
        long letters = query.codePoints().filter(Character::isLetter).count();
        long latin = query.codePoints().filter(Character::isLetter)
                .filter(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN).count();
        return letters == 0 || latin * 2 > letters;
    }
}
