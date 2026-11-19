package io.github.vedmider.sestacon.domain.model;

/** A retrieved chunk; {@code vectorScore}/{@code lexicalScore} are the reranker's inputs behind {@code score}. */
public record RetrievedChunk(String text, String source, double score, int sourceTokens,
                             Double vectorScore, Double lexicalScore) {

    public RetrievedChunk(String text, String source, double score, int sourceTokens) {
        this(text, source, score, sourceTokens, null, null);
    }

    public RetrievedChunk(String text, String source, double score) {
        this(text, source, score, 0);
    }

    public RetrievedChunk withSourceTokens(int sourceTokens) {
        return new RetrievedChunk(text, source, score, sourceTokens, vectorScore, lexicalScore);
    }

    public RetrievedChunk withSignals(double score, double vectorScore, double lexicalScore) {
        return new RetrievedChunk(text, source, score, sourceTokens, vectorScore, lexicalScore);
    }
}
