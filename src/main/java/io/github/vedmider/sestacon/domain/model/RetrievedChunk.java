package io.github.vedmider.sestacon.domain.model;

/**
 * A chunk returned from similarity search, with its source and relevance score. {@code sourceTokens}
 * is the estimated token size of the full source document the chunk came from (0 when not computed)
 * — it lets a federating caller credit the whole-document read it avoided by using the grounded chunk.
 */
public record RetrievedChunk(String text, String source, double score, int sourceTokens) {

    public RetrievedChunk(String text, String source, double score) {
        this(text, source, score, 0);
    }

    public RetrievedChunk withSourceTokens(int sourceTokens) {
        return new RetrievedChunk(text, source, score, sourceTokens);
    }
}
