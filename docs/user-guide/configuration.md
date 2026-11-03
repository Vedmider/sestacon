# Configuration

All settings live in `src/main/resources/application.yml` and are overridable via
environment variables (Spring relaxed binding) or Helm values. Environment variables
win, which is how the Helm chart injects configuration.

For the concrete maximums and constraints behind these settings (upload sizes, rate
limits, vector/retrieval limits, AI-provider restrictions, and what is *not* enforced),
see [Limits & Dimensioning](limits.md).

## Infrastructure connections

| Env var | Property | Default | Notes |
| --- | --- | --- | --- |
| `DB_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/ragdb` | pgvector-enabled Postgres |
| `DB_USERNAME` | `spring.datasource.username` | `rag` | |
| `DB_PASSWORD` | `spring.datasource.password` | `rag` | From a K8s Secret in production |
| `REDIS_HOST` | `spring.data.redis.host` | `localhost` | Semantic cache |
| `REDIS_PORT` | `spring.data.redis.port` | `6379` | |
| `SPRING_DATA_REDIS_PASSWORD` | `spring.data.redis.password` | _(unset)_ | Set when Redis auth is enabled |

Schema is managed by **Flyway** (`src/main/resources/db/migration`), which runs at
startup against the datasource. `V1` enables the `vector` extension; `V2` creates the
hybrid `rag_chunks` table, using placeholders fed by config:

| Env var | Flyway placeholder | Default | Notes |
| --- | --- | --- | --- |
| `EMBEDDING_DIMENSIONS` | `${embeddingDimensions}` | `384` | `vector(N)` column width — must equal the embedder's output |
| `RETRIEVAL_TS_CONFIG` | `${textSearchConfig}` | `english` | text-search config for the generated `tsvector` column |

Migrations are immutable once applied; to change the vector dimension or text config
after first run, add a new migration rather than editing `V2`.

## LLM (chat model)

| Env var | Property | Default | Notes |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | `spring.ai.openai.api-key` | `not-configured` | Placeholder lets the app boot; a real key is required for `/api/chat` |
| `OPENAI_BASE_URL` | `spring.ai.openai.base-url` | `https://api.groq.com/openai` | Any OpenAI-compatible endpoint. Do **not** include `/v1` — Spring AI appends `/v1/chat/completions`. |
| `LLM_MODEL` | `spring.ai.openai.chat.options.model` | `openai/gpt-oss-20b` | Must be a model your key can access (list: `GET {base-url}/v1/models`) |

### Local LLMs (Ollama, LM Studio, vLLM, …)

Any OpenAI-compatible server works through the same three settings — no code change and
no extra dependency, because the `ChatProvider` port is backed by the OpenAI-compatible
client. For a local [Ollama](https://ollama.com):

```bash
ollama pull llama3.1
OPENAI_BASE_URL=http://localhost:11434   # Spring AI appends /v1/chat/completions
LLM_MODEL=llama3.1
OPENAI_API_KEY=ollama                    # ignored by Ollama, but must be non-empty
```

From inside Kubernetes, point `OPENAI_BASE_URL` at the Ollama Service DNS name instead
of `localhost`. Ollama can also serve embeddings — set `EMBEDDING_PROVIDER=openai` and
`EMBEDDING_OPENAI_BASE_URL=http://localhost:11434` (ensure `EMBEDDING_DIMENSIONS` matches
the chosen embedding model).

## Embeddings and vector store

The embedding backend is selectable. By default it is the bundled local ONNX model; it
can be pointed at a different local model, or switched to an external OpenAI-compatible
embeddings API — all via configuration, no code change.

| Env var | Property | Default | Notes |
| --- | --- | --- | --- |
| `EMBEDDING_PROVIDER` | `app.embedding.provider` | `local` | `local` (bundled ONNX) or `openai` (external API) |
| `EMBEDDING_DIMENSIONS` | `app.embedding.dimensions` | `384` | Must equal the model's output **and** the pgvector column |
| `EMBEDDING_MODEL_URI` | `spring.ai.embedding.transformer.onnx.model-uri` | `classpath:/onnx/all-MiniLM-L6-v2/model.onnx` | `local` only — `classpath:`, `file:`, or `https:`. Point at a model you downloaded anywhere, e.g. `file:/opt/models/model.onnx`. The model is not committed to git — see [Getting Started ▸ Large files](getting-started.md#large-files-kept-out-of-git) |
| `EMBEDDING_TOKENIZER_URI` | `spring.ai.embedding.transformer.tokenizer.uri` | `classpath:/onnx/.../tokenizer.json` | `local` only |
| `EMBEDDING_MODEL_OUTPUT` | `spring.ai.embedding.transformer.onnx.model-output-name` | `last_hidden_state` | `local` only |
| `DJL_CACHE_DIR` | — (DJL env var) | `~/.djl.ai` | Where DJL stores/reads the native libtorch + tokenizer cache. Point at a pre-staged cache to run offline; the Docker image sets it to the baked `/opt/djl-cache` |
| `EMBEDDING_OPENAI_BASE_URL` | `app.embedding.openai.base-url` | `https://api.openai.com` | `openai` only |
| `EMBEDDING_OPENAI_MODEL` | `app.embedding.openai.model` | `text-embedding-3-small` | `openai` only |
| `EMBEDDING_API_KEY` | `app.embedding.openai.api-key` | _(unset)_ | `openai` only; inject via a Secret |

Changing the embedding model requires matching `EMBEDDING_DIMENSIONS` to the model and
**re-ingesting** existing documents (vectors from different models are not comparable).

### Swapping the embedding model — model-specific caveats

Pointing at a different ONNX model is config-only, but **the input convention and pooling
are family-specific and are not applied automatically**. A config-only swap works cleanly
for models that need no prefix and use mean pooling; other families need extra handling to
reach their rated quality.

| Model family | Dim | Input prefix needed | Pooling | Config-only swap? |
| --- | --- | --- | --- | --- |
| all-MiniLM-L6-v2 (default), all-mpnet, gte | 384 / 768 | none | mean | ✅ works out of the box |
| **e5 / multilingual-e5** | 384 / 768 / 1024 | **`"query: "` on queries, `"passage: "` on documents** | mean | ⚠️ loads, but **degraded without the prefixes** (a small code change) |
| **bge-en-v1.5** | 384 / 768 / 1024 | query-only instruction (`"Represent this sentence…"`) | **CLS** (Spring AI uses mean) | ⚠️ usable but **sub-optimal** (different prefix + pooling) |

Notes:
- **e5 prefixes are mandatory for quality.** This app currently embeds raw text with no
  prefix, so using e5 purely via config runs it in a degraded mode. Doing e5 properly needs
  the `query:`/`passage:` prefixes added in the embedding path (small code change), ideally
  exposed as config (`query-prefix` / `passage-prefix`) so it generalises to any family.
- **Dimension change is a re-index event:** update `EMBEDDING_DIMENSIONS`, the pgvector
  column width (the Flyway `embeddingDimensions` placeholder in `V2`), and **re-ingest**.
  Keep the dimension **≤ 2000** (pgvector HNSW limit).
- **Resources:** `*-base` (~1 GB) and `*-large` (~2 GB+) are much heavier than MiniLM on
  CPU — slower ingest, more RAM. Fine with a GPU.
- **Verify the artifact exists:** the model needs a real ONNX export **and** a compatible
  `tokenizer.json`.
- **Rule of thumb:** English-only → `bge-small/base-en-v1.5` or the default MiniLM;
  multilingual → `multilingual-e5-base` (with the prefix handling).

Other vector-store settings:

| Property | Default | Notes |
| --- | --- | --- |
| `spring.ai.vectorstore.pgvector.index-type` | `HNSW` | |
| `spring.ai.vectorstore.pgvector.distance-type` | `COSINE_DISTANCE` | |
| `spring.ai.vectorstore.pgvector.initialize-schema` | `true` | Auto-creates the `vector_store` table |

## RAG behaviour (`app.rag`)

| Property | Default | Notes |
| --- | --- | --- |
| `app.rag.top-k` | `4` | Chunks retrieved per question |
| `app.rag.system-prompt` | _(see yaml)_ | System instruction for the LLM |
| `app.rag.max-question-length` | `4000` | Max question characters; longer → `400` |

## Retrieval mode (`app.retrieval`)

Selects how chunks are retrieved. See [Data Model](../technical/data-model.md) for the
schemas and the fused SQL query.

| Property | Env var | Default | Notes |
| --- | --- | --- | --- |
| `app.retrieval.mode` | `RETRIEVAL_MODE` | `vector` | `vector` = Spring AI pgvector dense search (no RBAC). `hybrid` = dense + full-text + RBAC + RRF in one SQL round-trip, in a self-managed `rag_chunks` table. |
| `app.retrieval.rrf-k` | `RETRIEVAL_RRF_K` | `60` | Reciprocal Rank Fusion constant (`hybrid` only) |
| `app.retrieval.text-search-config` | `RETRIEVAL_TS_CONFIG` | `english` | PostgreSQL text-search config for `tsvector`/`tsquery` (`hybrid` only) |

In `hybrid` mode the caller's roles (`X-Roles` header on `/api/chat`) and each chunk's
`aclTags` enforce access control **inside the SQL query** — forbidden chunks are removed
before ranking, so they can never reach the LLM. In `vector` mode `aclTags` are stored
but not enforced.

## Semantic cache (`app.cache`)

| Property | Env var | Default | Notes |
| --- | --- | --- | --- |
| `app.cache.enabled` | — | `true` | Turn the cache off entirely |
| `app.cache.similarity-threshold` | `APP_CACHE_SIMILARITY_THRESHOLD` | `0.95` | Min cosine similarity for a cache hit |
| `app.cache.ttl-seconds` | — | `3600` | Per-entry TTL in Redis |
| `app.cache.max-entries` | — | `500` | FIFO eviction beyond this |

## Guardrails (`app.guardrails`)

| Property | Default | Notes |
| --- | --- | --- |
| `app.guardrails.enabled` | `true` | Master switch |
| `app.guardrails.redact-pii` | `true` | Redact email/phone/card/SSN before the LLM |
| `app.guardrails.block-prompt-injection` | `true` | Reject injection-pattern questions with `400` |
| `app.guardrails.filter-output` | `true` | Withhold answers containing banned phrases |
| `app.guardrails.banned-output-phrases` | `[]` | Phrases that trigger output withholding |

## Rate limiting (`app.rate-limit`)

| Property | Env var | Default | Notes |
| --- | --- | --- | --- |
| `app.rate-limit.enabled` | — | `true` | Applies to `/api/chat` and `/api/ingest` |
| `app.rate-limit.requests-per-minute` | `APP_RATE_LIMIT_REQUESTS_PER_MINUTE` | `60` | Per-client token bucket |
| `app.rate-limit.trust-forwarded-header` | — | `false` | Key on `X-Forwarded-For` first hop instead of the socket peer. Enable **only** behind a trusted proxy — the header is client-spoofable |

In-memory, per-pod. With multiple replicas the effective limit is approximately
`requests-per-minute × replicas`.

## Ingestion (`app.ingest`)

| Property | Default | Notes |
| --- | --- | --- |
| `app.ingest.max-file-size-bytes` | `10485760` (10 MB) | Rejected with `413` above this |
| `app.ingest.allowed-content-types` | pdf, text/plain, markdown | Rejected with `415` otherwise |
| `spring.servlet.multipart.max-file-size` | `25MB` | Servlet-level cap |
| `spring.servlet.multipart.max-request-size` | `100MB` | Servlet-level cap |

## Metrics / actuator endpoint

Actuator (`health`, `info`, `prometheus`) is exposed on the main server port in local dev.
In Kubernetes the Helm chart moves it to a **separate internal port**
(`app.managementPort`, default `8081`): probes and the ServiceMonitor target it, but the
Ingress only routes the API port — so `/actuator` is not reachable from outside the
cluster. Override via `MANAGEMENT_SERVER_PORT` if needed.

## Profiles

- `dev` (default): `/actuator/health` shows details when authorized.
- `prod`: health details hidden. The Helm chart sets `SPRING_PROFILES_ACTIVE=prod`.

## Helm values

When deploying, these map to chart values (e.g. `app.config.llm.baseUrl`,
`app.config.cache.similarityThreshold`, `app.config.rateLimit.requestsPerMinute`,
`postgres.auth.*`, `redis.auth.*`). See [Deployment](deployment.md) for the full
values reference.
