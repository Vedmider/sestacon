package io.github.vedmider.sestacon.domain.model;

import java.util.regex.Pattern;

/** Detects the grounded "I do not know" answer the RAG prompt asks for when the context lacks the answer. */
public final class Refusal {

    private static final Pattern REFUSAL = Pattern.compile(
            "(?iU)\\b(i\\s+(do\\s+not|don['’]?t|cannot|can['’]?t)\\s+(know|answer)|я\\s+не\\s+знаю|не\\s+знаю|не\\s+відомо)\\b");

    private Refusal() {
    }

    /** A refusal leads the answer or is the whole (short) answer; a refusal clause inside a real answer is not. */
    public static boolean isRefusal(String answer) {
        if (answer == null) {
            return false;
        }
        String a = answer.strip();
        var m = REFUSAL.matcher(a);
        return m.find() && (m.start() <= 2 || a.length() <= 80);
    }
}
