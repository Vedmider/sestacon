package io.github.vedmider.sestacon.application;

/**
 * Deterministic token estimate (whitespace + standalone punctuation split), mirroring the
 * federating client's tokenizer so served and baseline figures stay in the same unit.
 */
public final class TokenEstimate {

    private TokenEstimate() {
    }

    public static int count(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.replaceAll("([\\p{Punct}])", " $1 ").trim().split("\\s+").length;
    }
}
