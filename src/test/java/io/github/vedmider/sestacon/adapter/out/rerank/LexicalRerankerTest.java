package io.github.vedmider.sestacon.adapter.out.rerank;

import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LexicalRerankerTest {

    private static final String HANDBOOK = "This is the policy for the team on code review and what we expect from every "
            + "change. For each pull request the author and the reviewer agree on the scope.";

    private final LexicalReranker reranker = new LexicalReranker();

    private static List<RetrievedChunk> pool(String target, double targetSim) {
        List<RetrievedChunk> out = new ArrayList<>();
        out.add(new RetrievedChunk(target, "target.md", targetSim));
        String[] filler = {"Nightly database backups are retained for thirty days.",
                "Configure TLS by mounting the certificate and private key.",
                "Horizontal pod autoscaling adds replicas when CPU exceeds the target.",
                "Set the log level with an environment variable.",
                "During an incident the on-call engineer opens a bridge for the team."};
        for (int i = 0; i < filler.length; i++) {
            out.add(new RetrievedChunk(filler[i], "filler-" + i + ".md", 0.1));
        }
        return out;
    }

    private RetrievedChunk scored(String query, List<RetrievedChunk> candidates) {
        return reranker.rerank(query, candidates, candidates.size()).stream()
                .filter(c -> c.source().equals("target.md")).findFirst().orElseThrow();
    }

    @Test
    void sharingOnlyStopwordsAndGenericTermsIsNotAConfidentLexicalMatch() {
        RetrievedChunk c = scored("what is the policy for the team holidays and the parking", pool(HANDBOOK, 0.1));

        assertThat(c.lexicalScore()).isLessThan(0.5);
    }

    @Test
    void coveringEveryContentTermIsAFullLexicalMatch() {
        RetrievedChunk c = scored("what is the code review policy for the team", pool(HANDBOOK, 0.1));

        assertThat(c.lexicalScore()).isCloseTo(1.0, within(1e-9));
        assertThat(c.score()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void rareTermsWeighMoreThanTermsCommonAcrossTheCandidates() {
        List<RetrievedChunk> candidates = pool("The rollback procedure for the team.", 0.1);
        RetrievedChunk rare = scored("team rollback", candidates);
        RetrievedChunk common = reranker.rerank("team rollback", candidates, candidates.size()).stream()
                .filter(x -> x.source().equals("filler-4.md")).findFirst().orElseThrow();

        assertThat(rare.lexicalScore()).isGreaterThan(common.lexicalScore());
        assertThat(common.lexicalScore()).isLessThan(0.5);
    }

    @Test
    void cyrillicTermsMatchLexically() {
        RetrievedChunk c = scored("резервні копії бази даних",
                pool("Резервні копії бази даних створюються щоночі.", 0.1));

        assertThat(c.lexicalScore()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void aParaphraseWithNoSharedTermsKeepsItsVectorScore() {
        RetrievedChunk c = scored("undo a broken release", pool("Scale the old replicaset up.", 0.62));

        assertThat(c.vectorScore()).isCloseTo(0.62, within(1e-9));
        assertThat(c.lexicalScore()).isZero();
        assertThat(c.score()).isCloseTo(0.62, within(1e-9));
    }

    @Test
    void partialLexicalCoverageBelowTheMinimumIsNotEvidence() {
        RetrievedChunk c = scored("what is the policy for the team holidays and the parking", pool(HANDBOOK, 0.21));

        assertThat(c.lexicalScore()).isPositive().isLessThan(LexicalReranker.DEFAULT_MIN_LEXICAL_COVERAGE);
        assertThat(c.score()).isCloseTo(0.21, within(1e-9));
    }

    @Test
    void inflectedUkrainianFormsMatch() {
        RetrievedChunk c = scored("як подати заяву на відпустку",
                pool("Працівники подають заяву на відпустку через кадровий портал.", 0.1));

        assertThat(c.lexicalScore()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void nonLatinQueryDoesNotTrustAnEnglishOnlyEmbeddingSimilarity() {
        RetrievedChunk c = scored("яка політика щодо паркування автомобілів",
                pool("Резервні копії бази даних створюються щоночі.", 0.63));

        assertThat(c.vectorScore()).isCloseTo(0.63, within(1e-9));
        assertThat(c.score()).isZero();
    }

    @Test
    void nonLatinVectorSimilarityIsUsedWhenTheModelIsMultilingual() {
        LexicalReranker multilingual = new LexicalReranker(LexicalReranker.DEFAULT_MIN_LEXICAL_COVERAGE, true);
        List<RetrievedChunk> candidates = pool("Резервні копії бази даних створюються щоночі.", 0.63);

        RetrievedChunk c = multilingual.rerank("яка політика щодо паркування", candidates, candidates.size()).stream()
                .filter(x -> x.source().equals("target.md")).findFirst().orElseThrow();

        assertThat(c.score()).isCloseTo(0.63, within(1e-9));
    }

    @Test
    void resultsAreOrderedByRelevanceAndCutToTopK() {
        List<RetrievedChunk> out = reranker.rerank("code review policy", pool(HANDBOOK, 0.1), 2);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).source()).isEqualTo("target.md");
        assertThat(out.get(0).score()).isGreaterThanOrEqualTo(out.get(1).score());
    }
}
