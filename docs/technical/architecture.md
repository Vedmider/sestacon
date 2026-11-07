# Architecture

## Components

```
                        ┌──────────────── Kubernetes namespace ────────────────┐
   client ── Ingress ──▶│  Sestacon (Spring Boot)                                │
                        │   ├─ IngestController   /api/ingest (file upload)    │
                        │   ├─ DocumentController /api/documents (JSON chunks)  │
                        │   ├─ ChatController     /api/chat                     │
                        │   ├─ RagQueryService (orchestration)                  │
                        │   ├─ local ONNX embeddings (DJL, in-process)          │
                        │   ├─ guardrails · rate limiter · metrics              │
                        │   ├──SQL/vectors──▶ PostgreSQL + pgvector (PVC)        │
                        │   └──cache──▶ Redis                                   │
                        └───────────────────────────┬───────────────────────────┘
                                                     │ HTTPS (OpenAI-compatible)
                                                     ▼
                                      external LLM (Groq / OpenAI / …)
```

Packages follow a **hexagonal (ports & adapters)** layout:

| Package | Role | Contents |
| --- | --- | --- |
| `domain.model` | domain | Provider-agnostic records: `Answer`, `Completion`, `TokenUsage`, `DocumentChunk`, `RetrievedChunk`, `CachedAnswer` |
| `domain.port.in` | inbound ports | Use cases: `RagQueryUseCase`, `DocumentIngestionUseCase`, `IngestChunksUseCase` |
| `domain.port.out` | outbound ports | `ChatProvider`, `EmbeddingProvider`, `DocumentStore`, `AnswerCache`, `DocumentParser` |
| `application` | business logic | `RagQueryService`, `DocumentIngestionService`, `ChunkIngestionService` — depend only on ports |
| `adapter.in.web` | inbound adapter | `ChatController`, `IngestController`, `DocumentController` — map HTTP ↔ use cases |
| `adapter.out.ai` | outbound adapter | `SpringAiChatProvider`, `SpringAiEmbeddingProvider`, `OpenAiEmbeddingProvider` |
| `adapter.out.store` | outbound adapter | `PgVectorDocumentStore` (vector mode), `HybridPgDocumentStore` (hybrid mode) |
| `adapter.out.cache` | outbound adapter | `RedisAnswerCache` |
| `adapter.out.parser` | outbound adapter | `TikaDocumentParser` |
| `config` | wiring | `AdapterConfig` (auto-config), `@ConfigurationProperties` |
| `guardrail` | cross-cutting | PII redaction, prompt-injection detection, output filter |
| `ratelimit` | cross-cutting | Token-bucket limiter + MVC interceptor |
| `logging` | cross-cutting | Security audit log + per-request correlation (MDC) |
| `metrics` | cross-cutting | Micrometer meters for the RAG pipeline |

### Ports & adapters — why

The business logic (`application`) depends only on **ports** (interfaces in `domain.port`),
never on Spring AI, pgvector, Redis, or the web. Concrete technology lives in **adapters**
that implement those ports. This gives three properties:

1. **Swappable AI providers.** The LLM is behind `ChatProvider`; the embedder behind
   `EmbeddingProvider`. The default adapters wrap Spring AI (Groq/OpenAI, local ONNX), but
   you add a new provider by publishing a different `ChatProvider`/`EmbeddingProvider`
   bean — no change to the business logic. `AdapterConfig` registers each default
   `@ConditionalOnMissingBean`, so your bean transparently takes over.
2. **Business logic is testable in isolation** — `RagQueryServiceTest` runs the full RAG
   flow against in-memory fakes of the ports, with no Spring, DB, or network.
3. **I/O is separated from logic.** Controllers are thin inbound adapters that only map
   HTTP to use cases; stores/caches/parsers are outbound adapters. The core has no
   framework types in its signatures.

## Ingestion flow (`POST /api/ingest`)

1. Validate each file (type, size).
2. Apache Tika extracts text.
3. `TokenTextSplitter` splits text into chunks.
4. Each chunk is embedded locally (all-MiniLM-L6-v2, 384-dim) — the document never
   leaves the cluster for embedding.
5. Chunks + vectors + `source` metadata are written to pgvector.

Both ingestion paths converge on the same `DocumentChunk` **standard contract**
(`documentId`, `sectionPath`, `content`, `metadata`, `aclTags`) before the store. File
upload (`/api/ingest`) runs Tika + splitting; `POST /api/documents` accepts chunks that
are already in the contract, so any external producer (Jira, logs, ERP, a separate
parser service) plugs into the exact same shape without touching the search engine.

## Query flow (`POST /api/chat`)

1. **Guardrails — input**: reject prompt-injection patterns (`400`); redact PII from
   the question.
2. **Embed** the (redacted) question locally.
3. **Semantic cache**: if a prior question's embedding is within the similarity
   threshold, return the cached answer (`cached: true`) — no LLM call.
4. **Retrieve** the top-k most similar chunks. In the default `vector` mode this is a
   pgvector cosine KNN. In `hybrid` mode (`app.retrieval.mode=hybrid`) a single SQL
   round-trip fuses dense vectors + full-text (`tsvector`) + RBAC (`allowed_roles`,
   scoped by the `X-Roles` header) via Reciprocal Rank Fusion.
5. **Prompt**: build a grounded prompt (system instruction + retrieved context +
   question).
6. **LLM**: call the external OpenAI-compatible model.
7. **Guardrails — output**: withhold answers containing banned phrases.
8. **Cache + return**: store the result and return `{answer, sources, cached}`.

Metrics (latency, cache hits/misses, guardrail blocks, token usage) are recorded
throughout.

## Key design decisions

- **Local embeddings (ONNX via DJL).** Keeps document content and PII in-cluster,
  avoids per-embedding token cost, and removes a network dependency on the embedding
  path. The model is vendored in the jar and the DJL native libraries are baked into
  the image so startup is fully offline.
- **Vector dimension is 384** to match all-MiniLM-L6-v2. The pgvector schema
  dimension must equal the model's output size.
- **External LLM only for generation.** The chat model is pluggable through an
  OpenAI-compatible base URL (Groq by default), so the generation provider can be
  swapped without code changes.
- **Semantic (not exact) cache.** Caches by query-embedding similarity so
  paraphrased repeats also hit, maximising token/latency savings. The cache is
  best-effort: if Redis is unavailable the request proceeds without it, and the Redis
  health indicator is disabled so a cache outage never fails readiness.
- **Guardrails around the LLM boundary.** PII redaction and injection checks run
  before anything leaves the cluster; output filtering runs on the way back.
- **Single chart, independent components.** Postgres and Redis can be disabled to
  use external managed services; every image is overridable for private registries.

## Technology

Java 21 · Spring Boot 3.3 · Spring AI 1.0 · PostgreSQL + pgvector · Redis ·
Micrometer/Prometheus · Helm 3 / Kubernetes.
