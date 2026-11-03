package com.example.rag.support;

import com.example.rag.domain.model.Completion;
import com.example.rag.domain.model.TokenUsage;
import com.example.rag.domain.port.out.ChatProvider;

import java.util.concurrent.atomic.AtomicInteger;

/** Test double for {@link ChatProvider}: returns a canned answer, records the prompt
 *  it received, and counts calls — no external LLM. */
public class FakeChatProvider implements ChatProvider {

    private final String cannedAnswer;
    private volatile String lastUserPrompt;
    private volatile String lastSystemPrompt;
    private final AtomicInteger callCount = new AtomicInteger();

    public FakeChatProvider(String cannedAnswer) {
        this.cannedAnswer = cannedAnswer;
    }

    @Override
    public Completion complete(String systemPrompt, String userPrompt) {
        callCount.incrementAndGet();
        this.lastSystemPrompt = systemPrompt;
        this.lastUserPrompt = userPrompt;
        return new Completion(cannedAnswer, new TokenUsage(10, 5));
    }

    @Override
    public String name() {
        return "fake";
    }

    public String lastUserPrompt() {
        return lastUserPrompt;
    }

    public int callCount() {
        return callCount.get();
    }

    public void reset() {
        callCount.set(0);
        lastUserPrompt = null;
        lastSystemPrompt = null;
    }
}
