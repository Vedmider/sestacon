# Extending the Service

Practical recipes for changing each part. Read [Concepts](concepts.md) first so the
*why* behind each change is clear; this chapter is the *how*.

## Change the LLM / provider

Two ways, simplest first.

**1. Same adapter, different OpenAI-compatible endpoint (config only):**
```bash
# Groq (default), a different model your key can access:
--set app.config.llm.model=openai/gpt-oss-120b

# A different provider (e.g. xAI Grok) — note: no trailing /v1, Spring AI appends it:
--set app.config.llm.baseUrl=https://api.x.ai  --set app.config.llm.model=grok-2
```
List models your key can use: `GET {baseUrl}/v1/models`.

**2. A brand-new provider (new adapter, zero business-logic change):** implement the
`ChatProvider` port and publish it as a bean. `AdapterConfig` registers the Spring AI
default `@ConditionalOnMissingBean(ChatProvider.class)`, so your bean takes over:
```java
@Component
class MyProviderChatProvider implements ChatProvider {
    public Completion complete(String system, String user) {
        // call your SDK/HTTP API
        return new Completion(text, new TokenUsage(promptTokens, completionTokens));
    }
    public String name() { return "my-provider"; }
}
```
The application service and controllers are untouched — they depend only on the port.
The same pattern replaces any port: `EmbeddingProvider`, `DocumentStore`, `AnswerCache`,
`DocumentParser`.

## Change the embedding model

This is now config-driven — pick one of two paths.

**A. A different local ONNX model (incl. multilingual).** No rebuild needed if the model
is reachable by URI:
```bash
--set app.config.embedding.local.modelUri=https://my-host/models/multilingual.onnx
--set app.config.embedding.local.tokenizerUri=https://my-host/models/tokenizer.json
--set app.config.embedding.local.modelOutputName=last_hidden_state
--set app.config.embedding.dimensions=<model-output-dim>
```
URIs accept `classpath:` (baked), `file:` (mounted volume), or `https:`. To keep offline
startup, bake the new model into the image instead (replace the file under
`src/main/resources/onnx/...` and rebuild).

**B. An external OpenAI-compatible embeddings API** (hosted, often multilingual/higher
quality):
```bash
--set app.config.embedding.provider=openai
--set app.config.embedding.openai.baseUrl=https://api.openai.com
--set app.config.embedding.openai.model=text-embedding-3-small
--set app.config.embedding.dimensions=1536
--set app.secrets.embeddingApiKey.existingSecret=my-embed-key
```
This selects the `OpenAiEmbeddingProvider` adapter instead of the local one — the
business logic is unchanged (both implement `EmbeddingProvider`).

In **both** cases: set `app.config.embedding.dimensions` to the model's output size (it
drives the pgvector column), and **re-ingest** existing documents — vectors from a
different model are not comparable.

> Why re-ingest: cosine similarity is only meaningful between vectors from the *same*
> model. Mixing dimensions/models makes retrieval meaningless. See [Concepts §2–3](concepts.md).

To add a completely different embedding backend (e.g. Cohere), implement the
`EmbeddingProvider` port and publish it as a bean — `AdapterConfig` backs its defaults
off `@ConditionalOnMissingBean`.

## Swap the vector store

The code depends on Spring AI's `VectorStore` interface, not pgvector directly. To move
to another supported store (e.g. Qdrant, Redis vector, Milvus): change the starter
dependency and its `spring.ai.vectorstore.*` config. `DocumentIngestionService` and `RagQueryService`
need no changes. Keep the dimension aligned with the embedding model.

## Add or tune guardrails

- **New PII type**: add a rule to `PiiRedactor.RULES` (label, placeholder, regex).
  Mind ordering — broader digit patterns (card/SSN) must run before narrower ones
  (phone) so they aren't partially consumed.
- **New injection pattern**: add a compiled, case-insensitive regex to
  `PromptInjectionDetector.PATTERNS`.
- **Block output phrases**: set `app.guardrails.bannedOutputPhrases` (no code change).
- **Toggle**: every guardrail honours `app.guardrails.*` flags; disable individually or
  via `app.guardrails.enabled=false`.

For stronger safety, replace the heuristic detectors with a moderation model call behind
the same interfaces — the call sites in `RagQueryService` stay the same.

## Tune retrieval and caching

| Goal | Change |
| --- | --- |
| More/less context per answer | `app.rag.top-k` (default 4) |
| Different answer style/rules | `app.rag.system-prompt` |
| More cache hits (looser) | lower `app.cache.similarity-threshold` |
| Fewer, safer cache hits | raise `app.cache.similarity-threshold` |
| Longer/shorter cache memory | `app.cache.ttl-seconds`, `app.cache.max-entries` |
| Disable caching entirely | `app.cache.enabled=false` |

## Make rate limiting cluster-wide

The built-in limiter is per-pod (in-memory). For a precise global limit either enforce it
at the ingress/API gateway, or reimplement `RateLimiter` on top of Redis (atomic
`INCR` + per-window `EXPIRE`, keyed by client) — the `RateLimitInterceptor` contract
stays the same.

## Add a new endpoint / document type

- **New content type to ingest**: add it to `app.ingest.allowed-content-types`; Tika
  already parses many formats, so often no code change is needed.
- **New API**: add a `@RestController` under `adapter.in.web`; reuse the use-case ports
  (`RagQueryUseCase` / `DocumentIngestionUseCase`). If it should be
  rate-limited, add its path in `WebConfig.addInterceptors`.

## Test doubles (for your own tests)

Hermetic integration tests avoid real external calls using two doubles in
`src/test/java/.../support/`:

- `DeterministicEmbeddingModel` — a fake `EmbeddingModel` seeded by `text.hashCode()`:
  identical text → identical vector. Lets tests assert retrieval/cache behaviour without
  the ONNX model. (Trick: query with the *exact* stored text to force a cosine-1.0 hit.)
- `FakeChatProvider` — a fake of the `ChatProvider` port returning a canned answer and
  recording the prompt it received, so tests assert grounding and count calls — no API
  key, no network. (Business-logic unit tests use plain in-memory port fakes; see
  `RagQueryServiceTest`.)

Pattern: integration tests (`*IT`) start Testcontainers pgvector/Redis and provide these
beans, excluding the real transformers/OpenAI autoconfig. See `RagChatIT` and
`SemanticCacheIT`.

## Build & release notes

- The image bakes the ONNX model and DJL native libs for **offline** startup — after
  changing the embedding model, re-run `scripts/prefetch-djl-cache.sh` and rebuild.
- Dependencies resolve through a Maven mirror passed as a BuildKit secret; no credentials
  end up in image layers.
