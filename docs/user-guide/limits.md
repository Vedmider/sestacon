# Limits & Dimensioning

The hard and soft limits of the service: payload sizes, accepted formats, rate limits,
retrieval/vector constraints, AI-provider restrictions, and the things the app does
**not** enforce. Values are the shipped defaults (`application.yml`); most are
overridable — see [Configuration](configuration.md).

## Request & payload limits

| Limit | Default | Property / source | Enforced by | On exceed |
| --- | --- | --- | --- | --- |
| Max size per uploaded file | **10 MB** | `app.ingest.max-file-size-bytes` | `IngestController` | `413 Payload Too Large` |
| Servlet per-file cap | 25 MB | `spring.servlet.multipart.max-file-size` | Tomcat | `413` |
| Servlet total request cap (all files) | 100 MB | `spring.servlet.multipart.max-request-size` | Tomcat | `413` |
| Accepted upload content types | `application/pdf`, `text/plain`, `text/markdown`, `text/x-markdown` | `app.ingest.allowed-content-types` | `IngestController` | `415 Unsupported Media Type` |
| `/api/documents` JSON body size | **no explicit cap** | — | (heap only) | — |
| Question length | **4000 chars** | `app.rag.max-question-length` | `ChatController` | `400` if blank or over cap |
| Upload content type | must be present **and** allowlisted | `app.ingest.allowed-content-types` | `IngestController` | `415` on null/unknown/not-allowed |
| `X-Roles` count | no cap | — | — | — |

Notes:
- The effective per-file limit is the **smaller** of the app (10 MB) and servlet (25 MB)
  caps, i.e. 10 MB. Raise both together if you need larger files.
- **`/api/documents` has no size limit** beyond available heap — a very large JSON array
  of chunks is read fully into memory. Keep batches reasonable (e.g. ≤ a few thousand
  chunks) or chunk the calls. This is a known gap (see below).
- File type is checked by the declared `Content-Type`, not by sniffing bytes.

## Rate limits

| Limit | Default | Property |
| --- | --- | --- |
| Requests per minute per client | **60** | `app.rate-limit.requests-per-minute` |
| Scope (throttled paths) | `/api/chat`, `/api/ingest` | `ratelimit.WebConfig` |
| Client identity | socket peer address by default; `X-Forwarded-For` first hop only when `app.rate-limit.trust-forwarded-header=true` (set this only behind a trusted proxy) | `RateLimitInterceptor` |

- The limiter is an **in-memory, per-pod** token bucket. With N replicas the effective
  cluster limit is ≈ `requests-per-minute × N`; it is not shared across pods.
- **`/api/documents` is not rate-limited.** If you expose it publicly, throttle it at the
  ingress/gateway.
- Over-limit responses are `429 Too Many Requests` with a `Retry-After` header.

## Retrieval & RAG

| Limit | Default | Property |
| --- | --- | --- |
| Chunks retrieved per question (`top-k`) | **4** | `app.rag.top-k` |
| Retrieval mode | `vector` | `app.retrieval.mode` (`vector`\|`hybrid`) |
| Hybrid candidate pool per signal | `max(top-k × 5, 20)` | fixed in `HybridPgDocumentStore` |
| RRF constant `k` | 60 | `app.retrieval.rrf-k` |
| Full-text config | `english` | `app.retrieval.text-search-config` |

RBAC: a chunk is retrievable only if its `aclTags` contains `public` or intersects the
caller's `X-Roles`. **Enforced in SQL only in `hybrid` mode**; in `vector` mode `aclTags`
are stored but not applied.

## Embeddings & vectors

| Constraint | Value | Notes |
| --- | --- | --- |
| Vector dimension | **384** | `app.embedding.dimensions` / `EMBEDDING_DIMENSIONS`; must equal the model output, the pgvector column, and the Flyway placeholder |
| Bundled model | `all-MiniLM-L6-v2` (ONNX, in-process) | **English-centric** — see Language below |
| Max dimension for the HNSW index | **2000** (pgvector limit) | A larger embedding model must stay ≤ 2000 dims to keep the HNSW index |
| Raw bytes per vector | `dimensions × 4` (≈ 1.5 KB at 384) | Excludes content, metadata, tsvector |
| Changing the dimension/model | requires a **new migration + full re-ingest** | Vectors from different models are not comparable; migrations are immutable once applied |

**Language.** The bundled model is trained primarily on English; quality drops for other
languages. Swap to a multilingual model (e.g. `multilingual-e5-base`) via the
`EmbeddingProvider` port + config, keep the dimension aligned, and re-ingest — but mind the
per-model prefix/pooling caveats in
[Configuration ▸ Swapping the embedding model](configuration.md#swapping-the-embedding-model--model-specific-caveats)
(e.g. e5 needs `query:`/`passage:` prefixes to perform well).

## Chunking

Documents are split with Spring AI's `TokenTextSplitter` using its defaults (~800 tokens
per chunk). Chunk size is not currently exposed as config; change it in
`TikaDocumentParser` if needed. `/api/documents` producers control their own chunk sizes.

## Semantic cache (Redis)

| Limit | Default | Property |
| --- | --- | --- |
| Similarity threshold for a hit | 0.95 | `app.cache.similarity-threshold` |
| Per-entry TTL | 3600 s | `app.cache.ttl-seconds` |
| Max entries (FIFO eviction) | 500 | `app.cache.max-entries` |

Cache access is **best-effort**: any Redis error is swallowed and the request proceeds
without the cache. Lookup is linear over stored entries.

## AI provider (LLM) — what works and what's restricted

**Compatibility.** The chat model is reached through an **OpenAI-compatible** client, so
any endpoint speaking that protocol works with config only (no code change):

| Provider | Works | How |
| --- | --- | --- |
| Groq (default) | ✅ | `OPENAI_BASE_URL=https://api.groq.com/openai`, `LLM_MODEL=openai/gpt-oss-20b` |
| OpenAI | ✅ | `OPENAI_BASE_URL=https://api.openai.com` |
| Ollama (local) | ✅ | `OPENAI_BASE_URL=http://localhost:11434`, any pulled model |
| vLLM / LM Studio / other OpenAI-compatible | ✅ | point `OPENAI_BASE_URL` at it |
| Non-OpenAI-protocol SDKs (raw Anthropic, Bedrock, Vertex, …) | ⚠️ not out of the box | add a new `ChatProvider` adapter (the port exists for this) |

**Restrictions / things the app does not manage:**
- **An API key is required for `/api/chat`.** Without one the app still boots (placeholder
  `not-configured`); chat calls fail with `500`. Ingestion/health/metrics work keyless.
- **Context window and token limits are the model's, not the app's.** The app does not cap
  prompt tokens. The prompt = system prompt + up to `top-k` retrieved chunks (≈ `top-k ×
  ~800` tokens) + the question; this must fit the chosen model's context window. If you
  raise `top-k` or chunk size, verify it still fits.
- **Cost/quota/provider rate limits** are external and billed by the provider; the app
  does not meter spend. See the provider's pricing/console.
- Input guardrails may **reject** (`400`, prompt-injection) or **redact** (PII) the
  question before it reaches the model; output guardrails may **withhold** the answer.

## Database / storage growth (rough dimensioning)

Per chunk you store: the `content` text + a `dimensions × 4`-byte vector + a `tsvector`
(hybrid) + JSON metadata + indexes (HNSW on the vector, GIN on tsvector/roles). As a
rule of thumb, budget on the order of a few KB per chunk plus index overhead; HNSW index
memory grows with the number of vectors. Size the PostgreSQL PVC accordingly.

## Known limitations (not enforced)

- **No authentication** on any endpoint. `X-Roles` is trusted as sent — it is *access
  scoping*, not authentication. Put real auth (JWT/OAuth2) and TLS in front before
  exposing beyond a trusted network. (Rate limiting now keys on the socket peer by
  default, so `X-Forwarded-For` spoofing is a non-issue unless you opt in behind a proxy.)
- **`/api/documents` is neither rate-limited nor size-capped** — throttle/limit it at the
  gateway.
- **Rate limiting is per-pod**, not cluster-wide.
- **The embedding model is English-centric** by default.
- **Migrations are immutable** once applied; schema/dimension changes need a new migration
  (and re-ingest for dimension changes).
- **Prompt-injection detection is regex-based** (question + retrieved context are scanned,
  offending context chunks are dropped) — effective against common patterns, not a
  guarantee; consider a model-based check for high-assurance use.

## Security hardening in effect

- **Semantic cache is role-scoped** — entries are isolated per caller role-set, so a
  cached answer is never served across roles.
- **RBAC is enforced in both retrieval modes** — `vector` filters by `aclTags` metadata,
  `hybrid` filters in SQL. Public/untagged chunks are visible to everyone.
- **Ingested content can carry ACL tags** — `/api/ingest` accepts an `aclTags` field;
  `/api/documents` carries them in the contract (default `public`).
- **PII is redacted on both the question and the LLM answer**; card matches are
  Luhn-validated to avoid false positives.
- **Metrics/actuator run on a separate internal port** in Kubernetes (not routed by the
  Ingress) — see Configuration.
