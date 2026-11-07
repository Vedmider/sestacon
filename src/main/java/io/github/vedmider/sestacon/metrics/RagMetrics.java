package io.github.vedmider.sestacon.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class RagMetrics {

    private final Timer queryLatency;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter guardrailBlocks;
    private final Counter promptTokens;
    private final Counter completionTokens;
    private final MeterRegistry registry;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.queryLatency = Timer.builder("rag.query.latency")
                .description("End-to-end RAG query latency")
                .register(registry);
        this.cacheHits = Counter.builder("rag.cache.hits")
                .description("Semantic cache hits").register(registry);
        this.cacheMisses = Counter.builder("rag.cache.misses")
                .description("Semantic cache misses").register(registry);
        this.guardrailBlocks = Counter.builder("rag.guardrail.blocks")
                .description("Requests blocked by guardrails").register(registry);
        this.promptTokens = Counter.builder("rag.llm.tokens")
                .description("LLM token usage").tag("type", "prompt").register(registry);
        this.completionTokens = Counter.builder("rag.llm.tokens")
                .description("LLM token usage").tag("type", "completion").register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample) {
        sample.stop(queryLatency);
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    public void recordGuardrailBlock() {
        guardrailBlocks.increment();
    }

    public void recordTokens(long prompt, long completion) {
        if (prompt > 0) {
            promptTokens.increment(prompt);
        }
        if (completion > 0) {
            completionTokens.increment(completion);
        }
    }
}
