package com.example.rag.domain.model;

import java.util.List;

/** A cached answer together with the query embedding it was keyed on. */
public record CachedAnswer(String question, float[] embedding, String answer, List<String> sources) {
}
