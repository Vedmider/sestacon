package com.example.rag.domain.model;

/** The result of an LLM generation: the text plus token usage. */
public record Completion(String text, TokenUsage usage) {
}
