package com.example.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Selects the retrieval backend. {@code mode=vector} (default) uses Spring AI's pgvector
 * dense-vector search. {@code mode=hybrid} uses a single SQL round-trip combining dense
 * vectors, PostgreSQL full-text search, RBAC filtering and Reciprocal Rank Fusion.
 */
@ConfigurationProperties(prefix = "app.retrieval")
public class RetrievalProperties {

    /** vector | hybrid */
    private String mode = "vector";

    /** RRF rank constant (k): larger de-emphasises top ranks. */
    private int rrfK = 60;

    /** PostgreSQL text-search configuration used for tsvector/tsquery. */
    private String textSearchConfig = "english";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getRrfK() {
        return rrfK;
    }

    public void setRrfK(int rrfK) {
        this.rrfK = rrfK;
    }

    public String getTextSearchConfig() {
        return textSearchConfig;
    }

    public void setTextSearchConfig(String textSearchConfig) {
        this.textSearchConfig = textSearchConfig;
    }
}
