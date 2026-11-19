package io.github.vedmider.sestacon.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalyzerTest {

    @Test
    void tokenizesNonLatinScriptsAsWords() {
        assertThat(TextAnalyzer.terms("Резервні копії бази даних"))
                .containsExactlyInAnyOrder("резервні", "копії", "бази", "даних");
    }

    @Test
    void dropsEnglishAndUkrainianStopwords() {
        assertThat(TextAnalyzer.terms("What is the policy for the team and how")).containsExactlyInAnyOrder("policy", "team");
        assertThat(TextAnalyzer.terms("яка політика щодо паркування")).containsExactlyInAnyOrder("політика", "паркування");
    }

    @Test
    void foldsSimpleEnglishPluralsSoQueryAndTextAgree() {
        assertThat(TextAnalyzer.terms("adds replicas")).containsExactlyInAnyOrder("add", "replica");
        assertThat(TextAnalyzer.terms("add replica")).containsExactlyInAnyOrder("add", "replica");
        assertThat(TextAnalyzer.terms("access class")).containsExactlyInAnyOrder("access", "class");
    }

    @Test
    void keepsNumbersAndAccentedWords() {
        assertThat(TextAnalyzer.terms("café returns 429")).containsExactlyInAnyOrder("café", "return", "429");
    }

    @Test
    void nullOrBlankHasNoTerms() {
        assertThat(TextAnalyzer.terms(null)).isEmpty();
        assertThat(TextAnalyzer.terms("  ")).isEmpty();
    }
}
