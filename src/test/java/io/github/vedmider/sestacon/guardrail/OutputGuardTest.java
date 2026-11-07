package io.github.vedmider.sestacon.guardrail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutputGuardTest {

    private OutputGuard guard(boolean enabled, List<String> banned) {
        GuardrailProperties p = new GuardrailProperties();
        p.setFilterOutput(enabled);
        p.setBannedOutputPhrases(banned);
        return new OutputGuard(p);
    }

    @Test
    void withholdsWhenBannedPhrasePresentCaseInsensitive() {
        OutputGuard g = guard(true, List.of("Secret-Token"));
        assertThat(g.sanitize("here is the secret-token value")).isEqualTo(OutputGuard.WITHHELD);
    }

    @Test
    void passesCleanOutput() {
        OutputGuard g = guard(true, List.of("forbidden"));
        assertThat(g.sanitize("a perfectly fine answer")).isEqualTo("a perfectly fine answer");
    }

    @Test
    void disabledFilterAlwaysPasses() {
        OutputGuard g = guard(false, List.of("secret"));
        assertThat(g.sanitize("contains secret")).isEqualTo("contains secret");
    }

    @Test
    void nullOutputReturnedAsIs() {
        assertThat(guard(true, List.of("x")).sanitize(null)).isNull();
    }

    @Test
    void blankBannedEntryIsIgnored() {
        OutputGuard g = guard(true, List.of("", "  "));
        assertThat(g.sanitize("anything")).isEqualTo("anything");
    }
}
