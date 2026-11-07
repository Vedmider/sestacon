package io.github.vedmider.sestacon.guardrail;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OutputGuard {

    public static final String WITHHELD = "[response withheld by content policy]";

    private final GuardrailProperties properties;

    public OutputGuard(GuardrailProperties properties) {
        this.properties = properties;
    }

    public String sanitize(String output) {
        if (output == null || !properties.isFilterOutput()) {
            return output;
        }
        String lower = output.toLowerCase(Locale.ROOT);
        for (String banned : properties.getBannedOutputPhrases()) {
            if (!banned.isBlank() && lower.contains(banned.toLowerCase(Locale.ROOT))) {
                return WITHHELD;
            }
        }
        return output;
    }
}
