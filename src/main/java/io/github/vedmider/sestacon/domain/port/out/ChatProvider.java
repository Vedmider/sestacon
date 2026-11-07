package io.github.vedmider.sestacon.domain.port.out;

import io.github.vedmider.sestacon.domain.model.Completion;

/**
 * Outbound port for an LLM text-generation provider. Implement this (and expose it as a
 * bean) to plug in a new AI provider — the business logic depends only on this interface.
 */
public interface ChatProvider {

    Completion complete(String systemPrompt, String userPrompt);

    /** Short identifier of the backing provider, for logging/metrics. */
    String name();
}
