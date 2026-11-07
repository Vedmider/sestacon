package io.github.vedmider.sestacon.guardrail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void redactsEmail() {
        PiiRedactor.Result r = redactor.redact("reach me at jane.doe+test@example.co.uk please");
        assertThat(r.redacted()).isTrue();
        assertThat(r.redactedText()).doesNotContain("jane.doe").contains("[REDACTED_EMAIL]");
        assertThat(r.counts()).containsEntry("EMAIL", 1);
    }

    @Test
    void redactsCreditCardBeforePhoneSoDigitsAreNotSplit() {
        PiiRedactor.Result r = redactor.redact("card 4111 1111 1111 1111 now");
        assertThat(r.redactedText()).contains("[REDACTED_CC]").doesNotContain("4111");
        assertThat(r.counts()).containsKey("CREDIT_CARD").doesNotContainKey("PHONE");
    }

    @Test
    void redactsSsn() {
        PiiRedactor.Result r = redactor.redact("ssn 123-45-6789");
        assertThat(r.redactedText()).contains("[REDACTED_SSN]").doesNotContain("123-45-6789");
    }

    @Test
    void redactsUsPhone() {
        PiiRedactor.Result r = redactor.redact("call +1 (555) 123-4567 tomorrow");
        assertThat(r.redactedText()).contains("[REDACTED_PHONE]").doesNotContain("555");
    }

    @Test
    void redactsMultipleTypesAndCounts() {
        PiiRedactor.Result r = redactor.redact(
                "a@b.com and c@d.org, card 4111111111111111, ssn 111-22-3333");
        assertThat(r.counts()).containsEntry("EMAIL", 2).containsKey("CREDIT_CARD").containsKey("SSN");
        assertThat(r.redacted()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"What is the capital of France?", "no pii here 42 apples"})
    void leavesCleanTextUnchanged(String clean) {
        PiiRedactor.Result r = redactor.redact(clean);
        assertThat(r.redacted()).isFalse();
        assertThat(r.redactedText()).isEqualTo(clean);
        assertThat(r.counts()).isEmpty();
    }

    @Test
    void invalidLuhnCardIsNotRedacted() {
        PiiRedactor.Result r = redactor.redact("ref number 4111111111111112 here");
        assertThat(r.redactedText()).contains("4111111111111112");
        assertThat(r.counts()).doesNotContainKey("CREDIT_CARD");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(redactor.redact(null).redacted()).isFalse();
        assertThat(redactor.redact("").redacted()).isFalse();
    }
}
