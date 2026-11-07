package io.github.vedmider.sestacon.domain.model;

/** A chunk returned from similarity search, with its source and relevance score. */
public record RetrievedChunk(String text, String source, double score) {
}
