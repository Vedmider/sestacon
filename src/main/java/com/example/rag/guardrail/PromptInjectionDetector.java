package com.example.rag.guardrail;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class PromptInjectionDetector {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(the\\s+)?(previous|prior|above)\\s+(instructions|prompts?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(the\\s+)?(previous|prior|above)\\s+(instructions|prompts?)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(everything|all)\\s+(above|before|previous)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(your\\s+)?(system\\s+)?(prompt|instructions)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(show|print|repeat)\\s+(me\\s+)?(your\\s+)?(system\\s+)?(prompt|instructions)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override\\s+(the\\s+)?(instructions|rules|system)",
                    Pattern.CASE_INSENSITIVE)
    );

    public boolean isInjection(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        for (Pattern pattern : PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }
}
