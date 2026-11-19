package io.github.vedmider.sestacon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int topK = 4;
    private String systemPrompt = "You are a helpful assistant. Answer using only the provided context.";
    /** Context keeps chunks scoring at least this share of the top hit; 0 keeps all. */
    private double contextRelativeCut = 0.5;

    private int maxQuestionLength = 4000;

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getContextRelativeCut() {
        return contextRelativeCut;
    }

    public void setContextRelativeCut(double contextRelativeCut) {
        this.contextRelativeCut = contextRelativeCut;
    }

    public int getMaxQuestionLength() {
        return maxQuestionLength;
    }

    public void setMaxQuestionLength(int maxQuestionLength) {
        this.maxQuestionLength = maxQuestionLength;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
