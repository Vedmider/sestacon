package com.example.rag.guardrail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.guardrails")
public class GuardrailProperties {

    private boolean enabled = true;
    private boolean redactPii = true;
    private boolean blockPromptInjection = true;
    private boolean filterOutput = true;
    private List<String> bannedOutputPhrases = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRedactPii() {
        return redactPii;
    }

    public void setRedactPii(boolean redactPii) {
        this.redactPii = redactPii;
    }

    public boolean isBlockPromptInjection() {
        return blockPromptInjection;
    }

    public void setBlockPromptInjection(boolean blockPromptInjection) {
        this.blockPromptInjection = blockPromptInjection;
    }

    public boolean isFilterOutput() {
        return filterOutput;
    }

    public void setFilterOutput(boolean filterOutput) {
        this.filterOutput = filterOutput;
    }

    public List<String> getBannedOutputPhrases() {
        return bannedOutputPhrases;
    }

    public void setBannedOutputPhrases(List<String> bannedOutputPhrases) {
        this.bannedOutputPhrases = bannedOutputPhrases;
    }
}
