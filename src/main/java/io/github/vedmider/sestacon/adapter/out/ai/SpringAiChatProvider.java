package io.github.vedmider.sestacon.adapter.out.ai;

import io.github.vedmider.sestacon.domain.model.Completion;
import io.github.vedmider.sestacon.domain.model.TokenUsage;
import io.github.vedmider.sestacon.domain.port.out.ChatProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * {@link ChatProvider} backed by Spring AI's {@link ChatClient} (OpenAI-compatible,
 * e.g. Groq). Swap providers by exposing a different {@code ChatProvider} bean — this
 * adapter is only registered when none is already present (see {@code AdapterConfig}).
 */
public class SpringAiChatProvider implements ChatProvider {

    private final ChatClient chatClient;

    public SpringAiChatProvider(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Completion complete(String systemPrompt, String userPrompt) {
        ChatResponse response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .chatResponse();

        String text = response.getResult().getOutput().getText();
        return new Completion(text, usageOf(response));
    }

    @Override
    public String name() {
        return "spring-ai-openai";
    }

    private static TokenUsage usageOf(ChatResponse response) {
        if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return TokenUsage.EMPTY;
        }
        var u = response.getMetadata().getUsage();
        long prompt = u.getPromptTokens() != null ? u.getPromptTokens() : 0;
        long completion = u.getCompletionTokens() != null ? u.getCompletionTokens() : 0;
        return new TokenUsage(prompt, completion);
    }
}
