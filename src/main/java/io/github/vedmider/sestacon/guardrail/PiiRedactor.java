package io.github.vedmider.sestacon.guardrail;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class PiiRedactor {

    public record Result(String redactedText, boolean redacted, Map<String, Integer> counts) {
    }

    private record Rule(String label, String placeholder, Pattern pattern) {
    }

    // Order matters: card/SSN before phone so their digit runs are not consumed by the phone rule.
    private static final Rule[] RULES = {
            new Rule("CREDIT_CARD", "[REDACTED_CC]",
                    Pattern.compile("\\b(?:\\d[ -]?){13,16}\\b")),
            new Rule("SSN", "[REDACTED_SSN]",
                    Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b")),
            new Rule("EMAIL", "[REDACTED_EMAIL]",
                    Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
            new Rule("PHONE", "[REDACTED_PHONE]",
                    Pattern.compile("\\b(?:\\+?\\d{1,3}[-.\\s]?)?(?:\\(?\\d{3}\\)?[-.\\s]?)\\d{3}[-.\\s]?\\d{4}\\b"))
    };

    public Result redact(String input) {
        if (input == null || input.isEmpty()) {
            return new Result(input, false, Map.of());
        }
        String text = input;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            var matcher = rule.pattern().matcher(text);
            int count = 0;
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                if (rule.label().equals("CREDIT_CARD") && !luhnValid(matcher.group())) {
                    matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group()));
                    continue;
                }
                matcher.appendReplacement(sb, rule.placeholder());
                count++;
            }
            matcher.appendTail(sb);
            if (count > 0) {
                counts.put(rule.label(), count);
                text = sb.toString();
            }
        }
        return new Result(text, !counts.isEmpty(), counts);
    }

    /** Luhn checksum — avoids redacting arbitrary 13–16 digit runs that aren't real cards. */
    private static boolean luhnValid(String candidate) {
        String digits = candidate.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 16) {
            return false;
        }
        int sum = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (alt) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
