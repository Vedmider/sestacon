# Internals

Package and class design, the RAG pipeline step by step, and the core algorithms.
Base package: `com.example.rag`.

## Bootstrap

`RagApplication`:
- `@SpringBootApplication(exclude = OpenAiEmbeddingAutoConfiguration.class)` — the
  OpenAI starter would otherwise register a second `EmbeddingModel` bean and make the
  pgvector `EmbeddingModel` injection ambiguous. We use **local** embeddings only; the
  OpenAI starter is kept solely for the **chat** model.
- `@ConfigurationPropertiesScan` — binds the `app.*` property classes.

Bean wiring summary:
- `EmbeddingModel` ← Spring AI transformers autoconfig (ONNX, from the classpath model).
- `VectorStore` ← Spring AI pgvector autoconfig (needs `EmbeddingModel` + `DataSource`).
- `ChatClient.Builder` ← Spring AI chat-client autoconfig (needs one `ChatModel`, the
  OpenAI chat model).
- `StringRedisTemplate`, `ObjectMapper`, `MeterRegistry` ← Spring Boot autoconfig.

## Packages

| Package | Classes | Responsibility |
| --- | --- | --- |
| `domain.model` | `Answer`, `Completion`, `TokenUsage`, `DocumentChunk`, `RetrievedChunk`, `CachedAnswer` | Provider-agnostic domain types |
| `domain.port.in` | `RagQueryUseCase`, `DocumentIngestionUseCase`, `IngestChunksUseCase` | Inbound ports (use cases) |
| `domain.port.out` | `ChatProvider`, `EmbeddingProvider`, `DocumentStore`, `AnswerCache`, `DocumentParser` | Outbound ports |
| `application` | `RagQueryService`, `DocumentIngestionService`, `ChunkIngestionService` | Business logic (depends only on ports) |
| `adapter.in.web` | `ChatController`, `IngestController`, `DocumentController` | HTTP → use cases |
| `adapter.out.ai` | `SpringAiChatProvider`, `SpringAiEmbeddingProvider`, `OpenAiEmbeddingProvider` | LLM + embeddings (Spring AI) |
| `adapter.out.store` | `PgVectorDocumentStore`, `HybridPgDocumentStore` | pgvector dense store / hybrid vector+full-text+RBAC+RRF store |
| `adapter.out.cache` | `RedisAnswerCache` | Redis semantic cache |
| `adapter.out.parser` | `TikaDocumentParser` | Tika extraction + chunking |
| `config` | `AdapterConfig`, `*Properties` | Default adapter wiring + config binding |
| `guardrail` | `PiiRedactor`, `PromptInjectionDetector`, `OutputGuard`, `GuardrailException` | Safety at the LLM boundary |
| `ratelimit` | `RateLimiter`, `TokenBucket`, `RateLimitInterceptor`, `WebConfig` | Per-client throttling |
| `logging` | `SecurityAudit`, `RequestCorrelationFilter` | Security audit log + request correlation |
| `metrics` | `RagMetrics` | Micrometer meters |

## Ingestion pipeline (`DocumentIngestionService.ingest`)

```
MultipartFile bytes
  → NamedByteArrayResource (preserves filename for Tika)
  → TikaDocumentReader.get()           // extract text → List<Document>
  → set metadata["source"] = filename
  → TokenTextSplitter.apply(docs)      // split into token-bounded chunks
  → VectorStore.add(chunks)            // embeds (local) + inserts rows
  → return chunk count
```

`IngestController` validates each file first: non-empty (`400`), size ≤
`app.ingest.max-file-size-bytes` (`413`), content type ∈ allow-list (`415`). A servlet
multipart cap (`spring.servlet.multipart.*`) is a second guard.

## Query pipeline (`RagQueryService.answer`)

Wrapped in a Micrometer timer (`rag.query.latency`). Steps:

1. **Injection guard** — `PromptInjectionDetector.isInjection(raw)`; if matched,
   record `rag.guardrail.blocks` and throw `GuardrailException` → `400`.
2. **PII redaction** — `PiiRedactor.redact(raw)`; the **redacted** text is used for
   everything downstream, so raw PII is never embedded, cached, or sent to the LLM.
3. **Embed** the question locally (`EmbeddingModel.embed`).
4. **Cache lookup** — `SemanticCache.findSimilar(embedding)`; on hit record
   `rag.cache.hits` and return `ChatAnswer(answer, sources, cached=true)`.
5. **Miss** — record `rag.cache.misses`; `VectorStore.similaritySearch(topK)`.
6. **Prompt** — `ChatClient` with the system prompt + a user template filled with the
   joined chunk texts and the question.
7. **LLM call** — `.call().chatResponse()`; token usage is read from
   `response.getMetadata().getUsage()` into `rag.llm.tokens{type}`.
8. **Output guard** — `OutputGuard.sanitize(answer)`.
9. **Store + return** — `SemanticCache.store(...)`; return `cached=false` with distinct
   `sources`.

Each guardrail/cache step is gated by its `*.enabled` flag, so features can be toggled
independently.

## Algorithms

### Embeddings
ONNX `all-MiniLM-L6-v2` via DJL. Tokenise → model → `last_hidden_state` → mean-pool
over tokens → L2-normalise → 384-dim vector. In-process; no network.

### Semantic cache (`SemanticCache`)
- Store: `entry = {question, embedding, answer, sources}` → JSON at
  `ragcache:entry:<uuid>` with TTL; `LPUSH` the id onto `ragcache:index`; evict from the
  tail (`RPOP` + delete) while the list exceeds `maxEntries` (FIFO).
- Lookup: `LRANGE` all ids, load each entry, compute cosine similarity to the query
  embedding, return the best entry with similarity ≥ `similarityThreshold`.
- Resilience: every Redis op is wrapped in try/catch; failures log a warning and behave
  as a miss / no-op so the request still completes.
- Complexity: O(n) per query over cached entries (n ≤ `maxEntries`, default 500).

### Cosine similarity
`dot(a,b) / (‖a‖·‖b‖)`, guarded against zero-norm vectors (returns −1). Used by both the
cache and (via pgvector's `<=>`) retrieval.

### Token-bucket rate limiter (`TokenBucket` / `RateLimiter`)
- One bucket per client key in a `ConcurrentHashMap`; capacity =
  `requestsPerMinute`, refill = `requestsPerMinute / 60` tokens per second.
- `tryConsume()` refills based on elapsed `System.nanoTime()` then removes one token if
  available. `synchronized` for thread safety.
- `RateLimitInterceptor` runs before `/api/chat` and `/api/ingest`; client key =
  first `X-Forwarded-For` hop, else remote address. On empty bucket → `429` +
  `Retry-After: ceil(60 / rpm)`.
- **Per-pod** (in-memory). With N replicas the effective limit ≈ `rpm × N`.

### PII redaction (`PiiRedactor`)
Regex replacement applied in a fixed order — **credit card → SSN → email → phone** — so
long digit runs (cards) aren't partially consumed by the phone pattern. Produces
`Result(redactedText, redacted, counts)` with placeholders `[REDACTED_CC|SSN|EMAIL|PHONE]`.

### Prompt-injection detection (`PromptInjectionDetector`)
Case-insensitive match against a list of patterns (ignore/disregard/forget previous
instructions, reveal/show system prompt, "you are now", override rules). Heuristic, not
a model — cheap and deterministic.

### Output guard (`OutputGuard`)
Substring check against `bannedOutputPhrases`; any match returns the sentinel
`OutputGuard.WITHHELD`. Empty list by default (no-op).

## Metrics (`RagMetrics`)

| Meter | Type | Incremented in |
| --- | --- | --- |
| `rag.query.latency` | Timer | `RagQueryService.answer` (whole call) |
| `rag.cache.hits` / `rag.cache.misses` | Counter | cache lookup branch |
| `rag.guardrail.blocks` | Counter | injection block |
| `rag.llm.tokens{type=prompt\|completion}` | Counter | after the LLM response |

Exposed at `/actuator/prometheus`. In tests the endpoint requires
`@AutoConfigureObservability` (Spring Boot disables metrics export in `@SpringBootTest`
by default).

## Error handling

- Validation/guardrail failures use `ResponseStatusException` /
  `@ResponseStatus(BAD_REQUEST)` (`GuardrailException`) → clean 4xx.
- LLM/transport failures propagate as `500` (Spring AI retry wraps provider errors in
  `NonTransientAiException`).

## Tests

Three layers, run by `mvn -B verify`, with a JaCoCo coverage gate (build fails under
88% line / 60% branch, measured on the merged unit+integration report). 104 tests total:

- **Unit** (`*Test`, Surefire): pure logic with no Spring — guardrails
  (`PiiRedactorTest`, `PromptInjectionDetectorTest`, `OutputGuardTest`), rate limiter
  (`RateLimitTest`), business logic (`RagQueryServiceTest`, `DocumentIngestionServiceTest`),
  adapters (`PgVectorDocumentStoreTest` and `RedisAnswerCacheTest` via Mockito,
  `OpenAiEmbeddingProviderTest` via `MockRestServiceServer`, `TikaDocumentParserTest`),
  logging (`SecurityAuditTest`, `RequestCorrelationFilterTest`), and the embedding model.
- **Component / contract** (`@WebMvcTest`, Surefire): controllers in isolation with mocked
  use cases — behaviour (`ChatControllerWebTest`, `IngestControllerWebTest`,
  `DocumentControllerWebTest`) and strict wire-contract (`ChatApiContractTest`,
  `IngestApiContractTest`: media types, JSON schema, status matrix). A lightweight,
  framework-free alternative to Spring Cloud Contract.
- **Integration** (`*IT`, Failsafe, Testcontainers pgvector + Redis): `PgVectorStoreIT`,
  `HybridPgDocumentStoreIT` (vector+full-text+RBAC+RRF against real pgvector),
  `IngestionIT`, `RagChatIT` (incl. OpenAPI + metrics), `SemanticCacheIT`.

Test doubles keep integration hermetic (no external LLM, no model download):
- `DeterministicEmbeddingModel` — fake `EmbeddingModel` seeded by `text.hashCode()`:
  identical text → identical vector. Querying with the *exact* stored text forces a
  cosine-1.0 retrieval/cache hit deterministically.
- `FakeChatProvider` — fake `ChatProvider` (our port) that returns a canned answer, records the
  prompt it received, and counts calls (so tests assert grounding and cache behaviour
  without an API key). `reset()` clears state between methods; `SemanticCacheIT` also
  flushes Redis in `@BeforeEach` for isolation.

Coverage report after a run: `target/site/jacoco-merged/index.html`.

End-to-end scripts (outside the Maven build): `scripts/e2e-compose.sh` (offline, no key)
and `scripts/e2e-full.sh` (downloads a real doc and asks the live LLM). See
[Extending](extending.md) and the [User Guide](../user-guide/getting-started.md).
