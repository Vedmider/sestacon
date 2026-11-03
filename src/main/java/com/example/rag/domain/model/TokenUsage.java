package com.example.rag.domain.model;

/** Provider-agnostic token accounting for one LLM call. */
public record TokenUsage(long promptTokens, long completionTokens) {

    public static final TokenUsage EMPTY = new TokenUsage(0, 0);
}
