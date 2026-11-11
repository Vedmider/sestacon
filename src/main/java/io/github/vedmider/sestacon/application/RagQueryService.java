package io.github.vedmider.sestacon.application;

import io.github.vedmider.sestacon.config.RagProperties;
import io.github.vedmider.sestacon.domain.model.Answer;
import io.github.vedmider.sestacon.domain.model.CachedAnswer;
import io.github.vedmider.sestacon.domain.model.Completion;
import io.github.vedmider.sestacon.domain.model.RetrievedChunk;
import io.github.vedmider.sestacon.domain.port.in.RagQueryUseCase;
import io.github.vedmider.sestacon.domain.port.in.RetrieveUseCase;
import io.github.vedmider.sestacon.domain.port.out.AnswerCache;
import io.github.vedmider.sestacon.domain.port.out.ChatProvider;
import io.github.vedmider.sestacon.domain.port.out.EmbeddingProvider;
import io.github.vedmider.sestacon.guardrail.GuardrailException;
import io.github.vedmider.sestacon.guardrail.GuardrailProperties;
import io.github.vedmider.sestacon.guardrail.OutputGuard;
import io.github.vedmider.sestacon.guardrail.PiiRedactor;
import io.github.vedmider.sestacon.guardrail.PromptInjectionDetector;
import io.github.vedmider.sestacon.logging.SecurityAudit;
import io.github.vedmider.sestacon.metrics.RagMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Core RAG business logic. Depends only on domain ports — it has no knowledge of the
 * concrete AI provider, vector store, cache, or the web layer.
 */
@Service
public class RagQueryService implements RagQueryUseCase {

    private static final String USER_TEMPLATE = """
            Use the following context to answer the question. If the answer is not
            contained in the context, say you do not know.

            Context:
            %s

            Question: %s
            """;

    private final ChatProvider chatProvider;
    private final EmbeddingProvider embeddingProvider;
    private final RetrieveUseCase retrieve;
    private final AnswerCache answerCache;
    private final RagProperties properties;
    private final GuardrailProperties guardrails;
    private final PiiRedactor piiRedactor;
    private final PromptInjectionDetector injectionDetector;
    private final OutputGuard outputGuard;
    private final RagMetrics metrics;

    public RagQueryService(ChatProvider chatProvider, EmbeddingProvider embeddingProvider,
                           RetrieveUseCase retrieve, AnswerCache answerCache,
                           RagProperties properties, GuardrailProperties guardrails,
                           PiiRedactor piiRedactor, PromptInjectionDetector injectionDetector,
                           OutputGuard outputGuard, RagMetrics metrics) {
        this.chatProvider = chatProvider;
        this.embeddingProvider = embeddingProvider;
        this.retrieve = retrieve;
        this.answerCache = answerCache;
        this.properties = properties;
        this.guardrails = guardrails;
        this.piiRedactor = piiRedactor;
        this.injectionDetector = injectionDetector;
        this.outputGuard = outputGuard;
        this.metrics = metrics;
    }

    @Override
    public Answer answer(String rawQuestion, List<String> roles) {
        Timer.Sample sample = metrics.startTimer();
        try {
            return doAnswer(rawQuestion, roles);
        }
        finally {
            metrics.stopTimer(sample);
        }
    }

    private Answer doAnswer(String rawQuestion, List<String> roles) {
        boolean guard = guardrails.isEnabled();

        if (guard && guardrails.isBlockPromptInjection() && injectionDetector.isInjection(rawQuestion)) {
            metrics.recordGuardrailBlock();
            SecurityAudit.promptInjectionBlocked();
            throw new GuardrailException("Request blocked: possible prompt injection detected");
        }

        String question = rawQuestion;
        if (guard && guardrails.isRedactPii()) {
            PiiRedactor.Result redaction = piiRedactor.redact(rawQuestion);
            if (redaction.redacted()) {
                SecurityAudit.piiRedacted(redaction.counts());
            }
            question = redaction.redactedText();
        }

        float[] questionEmbedding = embeddingProvider.embed(question);

        Optional<CachedAnswer> cached = answerCache.findSimilar(questionEmbedding, roles);
        if (cached.isPresent()) {
            metrics.recordCacheHit();
            CachedAnswer hit = cached.get();
            return new Answer(hit.answer(), hit.sources(), true);
        }
        metrics.recordCacheMiss();

        List<RetrievedChunk> chunks = retrieve.retrieve(question, properties.getTopK(), roles);
        if (guard && guardrails.isBlockPromptInjection()) {
            chunks = chunks.stream().filter(c -> {
                if (injectionDetector.isInjection(c.text())) {
                    SecurityAudit.contextInjectionDropped(c.source());
                    return false;
                }
                return true;
            }).toList();
        }
        String context = chunks.stream()
                .map(RetrievedChunk::text)
                .reduce((a, b) -> a + "\n---\n" + b)
                .orElse("");

        String userPrompt = USER_TEMPLATE.formatted(context, question);
        Completion completion = chatProvider.complete(properties.getSystemPrompt(), userPrompt);
        metrics.recordTokens(completion.usage().promptTokens(), completion.usage().completionTokens());

        String answer = completion.text();
        if (guard && guardrails.isFilterOutput()) {
            String sanitized = outputGuard.sanitize(answer);
            if (!sanitized.equals(answer)) {
                SecurityAudit.outputWithheld();
            }
            answer = sanitized;
        }
        if (guard && guardrails.isRedactPii()) {
            PiiRedactor.Result outRedaction = piiRedactor.redact(answer);
            if (outRedaction.redacted()) {
                SecurityAudit.piiRedacted(outRedaction.counts());
            }
            answer = outRedaction.redactedText();
        }

        List<String> sources = chunks.stream().map(RetrievedChunk::source).distinct().toList();
        answerCache.store(new CachedAnswer(question, questionEmbedding, answer, sources), roles);
        return new Answer(answer, sources, false);
    }
}
