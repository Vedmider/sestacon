package io.github.vedmider.sestacon.domain.model;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared lexical analysis for ranking and judging: Unicode words, case-folded, stopwords dropped, plurals folded. */
public final class TextAnalyzer {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern LATIN = Pattern.compile("[a-z0-9]+");

    private static final Set<String> STOPWORDS = Set.copyOf(java.util.Arrays.asList(
            // English
            "a", "about", "after", "all", "also", "am", "an", "and", "any", "are", "as", "at", "be", "been", "before",
            "being", "but", "by", "can", "could", "did", "do", "does", "doing", "done", "each", "every", "for", "from",
            "had", "has", "have", "having", "he", "her", "here", "his", "how", "i", "if", "in", "into", "is", "it",
            "its", "just", "may", "me", "might", "more", "most", "must", "my", "no", "not", "of", "on", "once", "only",
            "or", "other", "our", "out", "over", "own", "shall", "she", "should", "so", "some", "such", "than", "that",
            "the", "their", "them", "then", "there", "these", "they", "this", "those", "through", "to", "too", "under",
            "until", "up", "us", "very", "was", "we", "were", "what", "when", "where", "which", "while", "who", "whom",
            "why", "will", "with", "would", "you", "your",
            // Ukrainian
            "і", "й", "та", "а", "але", "в", "у", "з", "із", "зі", "на", "до", "від", "для", "по", "про", "при", "що",
            "щодо", "як", "яка", "який", "яке", "які", "хто", "де", "коли", "чи", "це", "цей", "ця", "ці", "той", "та",
            "не", "ні", "так", "же", "би", "б", "або", "його", "її", "їх", "ми", "ви", "він", "вона", "воно", "вони",
            "я", "ти", "мені", "нам", "вам", "є", "був", "була", "було", "були", "буде", "через", "після", "перед",
            // Russian
            "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то", "все", "она", "так", "его", "но",
            "да", "ты", "к", "у", "же", "вы", "за", "бы", "по", "только", "ее", "мне", "было", "вот", "от", "меня",
            "о", "из", "ему", "для", "или", "кто", "где", "какая", "какой", "это", "этот", "эта", "эти"));

    private TextAnalyzer() {
    }

    /** Distinct content terms of {@code text}, in first-seen order. */
    public static Set<String> terms(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            if (t.length() < 2 || STOPWORDS.contains(t)) {
                continue;
            }
            out.add(foldPlural(t));
        }
        return out;
    }

    private static String foldPlural(String t) {
        if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss") && LATIN.matcher(t).matches()) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }
}
