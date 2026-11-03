# Data Model & Schemas

All persistent and wire schemas: the standard ingestion contract, the pgvector table,
the hybrid `rag_chunks` table, the Redis cache layout, the embedding vectors, and the
HTTP DTOs.

## The standard ingestion contract (`DocumentChunk`)

Everything that enters the store — whether produced by the built-in Tika file parser or
pushed by an external source (Jira, logs, automotive manuals, ERP) — is normalised to a
single, source-agnostic shape. This is what decouples *producing* chunks (parsing) from
*serving* them (search): any producer that emits this contract plugs in unchanged.

```java
record DocumentChunk(
    String documentId,            // stable id of the source document; used as the citation source
    String sectionPath,           // logical location, e.g. "Chapter 2 > Rate limits"
    String content,               // the text to embed and search
    Map<String,Object> metadata,  // arbitrary extra fields, stored as JSON, filterable
    List<String> aclTags)         // roles/groups allowed to read it; "public" = everyone
```

Defaults applied in the compact constructor: `metadata` → `{}`, and an empty/absent
`aclTags` → `["public"]`. The JSON wire form of this contract is accepted directly at
`POST /api/documents` (see HTTP DTOs below).

## PostgreSQL / pgvector

The Spring AI `PgVectorStore` auto-creates the `vector_store` table on startup
(`spring.ai.vectorstore.pgvector.initialize-schema=true`). Required extensions:
`vector`, `hstore`, `uuid-ossp`.

> **Schema ownership.** `vector_store` is library-managed by Spring AI. Everything this
> app authors — the shared `vector` extension and the hybrid `rag_chunks` table — is
> versioned with **Flyway** (`src/main/resources/db/migration`). The two coexist: Flyway
> tracks only its own history, and both create the `vector` extension idempotently.

### Table `vector_store` (actual DDL)

```
  Column   |    Type     | Nullable |      Default
-----------+-------------+----------+--------------------
 id        | uuid        | not null | uuid_generate_v4()
 content   | text        |          |
 metadata  | json        |          |
 embedding | vector(384) |          |
Indexes:
    "vector_store_pkey" PRIMARY KEY, btree (id)
    "spring_ai_vector_index" hnsw (embedding vector_cosine_ops)
```

| Column | Meaning |
| --- | --- |
| `id` | Chunk UUID (generated). |
| `content` | The chunk text (one row per chunk). |
| `metadata` | JSON; this app sets `source`, `documentId`, `sectionPath` and `aclTags` (comma-joined) from the `DocumentChunk`. Extra keys from the chunk's `metadata` are merged in. |
| `embedding` | 384-dim `vector`; cosine distance via the HNSW index. |

> The vector dimension (384) must equal the embedding model's output size
> (`spring.ai.vectorstore.pgvector.dimensions`). Changing the embedding model means
> changing this and re-indexing.

### Example row

```
id        d164e3a2-1df6-42bc-992d-96f5aa1b88a1
content   "Kubernetes orchestrates containers. Helm ..."
metadata  {"source": "s.txt"}
embedding [0.16291933, 0.018191477, -0.025..., …]   -- 384 floats
```

### Retrieval query (conceptual)

`VectorStore.similaritySearch` issues, in effect:

```sql
SELECT id, content, metadata,
       1 - (embedding <=> :queryEmbedding) AS similarity
FROM vector_store
WHERE 1 - (embedding <=> :queryEmbedding) >= :threshold   -- default threshold 0.0
ORDER BY embedding <=> :queryEmbedding                    -- <=> = cosine distance
LIMIT :topK;                                              -- app.rag.top-k (default 4)
```

The default vector store does **not** enforce RBAC — the `aclTags` are stored but not
used to filter. For RBAC + full-text + RRF, switch to the hybrid store below.

## Hybrid retrieval — table `rag_chunks` (mode `hybrid`)

Set `app.retrieval.mode=hybrid` to select a store that combines dense vectors,
PostgreSQL full-text search, RBAC and Reciprocal Rank Fusion in **one SQL round-trip**.
Its table is created by **Flyway migration `V2__hybrid_rag_chunks.sql`** (the `vector`
extension by `V1`), and it embeds content/queries via the `EmbeddingProvider` port
directly rather than through Spring AI's `VectorStore`. The `vector(N)` dimension and the
text-search config are Flyway placeholders (`${embeddingDimensions}`, `${textSearchConfig}`)
fed by the same `EMBEDDING_DIMENSIONS` / `RETRIEVAL_TS_CONFIG` env vars as the app, so the
column and the embedder cannot drift.

```sql
CREATE TABLE rag_chunks (
    id            BIGSERIAL PRIMARY KEY,
    document_id   TEXT NOT NULL,
    section_path  TEXT NOT NULL DEFAULT '',
    content       TEXT NOT NULL,
    metadata      JSONB NOT NULL DEFAULT '{}'::jsonb,
    allowed_roles TEXT[] NOT NULL DEFAULT ARRAY['public'],      -- from DocumentChunk.aclTags
    embedding     vector(384) NOT NULL,                         -- dense semantic vector
    content_tsv   tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED
);
CREATE INDEX rag_chunks_tsv_idx       ON rag_chunks USING GIN  (content_tsv);     -- full-text
CREATE INDEX rag_chunks_roles_idx     ON rag_chunks USING GIN  (allowed_roles);   -- RBAC
CREATE INDEX rag_chunks_embedding_idx ON rag_chunks USING hnsw (embedding vector_cosine_ops);
```

### Why three signals

| Signal | Index | Catches |
| --- | --- | --- |
| Dense vector (`embedding <=>`) | HNSW | *Conceptual* matches — "crash" ≈ "failure". |
| Full-text (`content_tsv @@ query`) | GIN | *Exact* tokens vectors scramble — part numbers, error codes like `ERR-503-GATEWAY`. |
| `allowed_roles && caller_roles` | GIN | RBAC — removes forbidden rows *before* ranking, so the LLM never sees them. |

### The fused query (RRF)

The caller's roles are always widened with `public`. Each signal produces its own
ranked list (capped at a candidate pool); the two lists are merged by **Reciprocal Rank
Fusion**: `score = Σ 1 / (k + rank_i)`, where `k` is `app.retrieval.rrf-k` (default 60).
RRF needs no score normalisation — it only uses ranks — which is why it safely combines
cosine distance with `ts_rank`.

```sql
WITH vec AS (                                  -- dense-vector ranked list (RBAC-filtered)
    SELECT id, document_id, content,
           ROW_NUMBER() OVER (ORDER BY embedding <=> :q) AS rnk
    FROM rag_chunks
    WHERE allowed_roles && :roles
    ORDER BY embedding <=> :q LIMIT :pool
),
txt AS (                                       -- full-text ranked list (RBAC-filtered)
    SELECT id, document_id, content,
           ROW_NUMBER() OVER (ORDER BY ts_rank(content_tsv, q.query) DESC) AS rnk
    FROM rag_chunks, websearch_to_tsquery('english', :text) AS q(query)
    WHERE allowed_roles && :roles AND content_tsv @@ q.query
    ORDER BY ts_rank(content_tsv, q.query) DESC LIMIT :pool
)
SELECT COALESCE(v.document_id, t.document_id) AS document_id,
       COALESCE(v.content, t.content)         AS content,
       COALESCE(1.0/(:k + v.rnk),0) + COALESCE(1.0/(:k + t.rnk),0) AS score   -- RRF
FROM vec v FULL OUTER JOIN txt t ON v.id = t.id
ORDER BY score DESC LIMIT :topK;
```

Because the `allowed_roles && :roles` predicate sits in both CTEs' `WHERE`, forbidden
rows are eliminated by the query planner before any distance or rank computation — a
caller without the `engineering` role can never retrieve an `engineering`-only chunk,
regardless of how well it matches.


## Redis — semantic cache

Keys live under the `ragcache:` namespace.

| Key | Type | Contents |
| --- | --- | --- |
| `ragcache:index` | List | UUIDs of cache entries, newest first (`LPUSH`). Trimmed from the tail (`RPOP`) past `app.cache.max-entries`. |
| `ragcache:entry:<uuid>` | String (JSON) | One `CacheEntry`, with TTL = `app.cache.ttl-seconds`. |

### `CacheEntry` JSON schema

```json
{
  "question": "redacted question text",
  "embedding": [0.0123, -0.0456, "…384 floats…"],
  "answer": "the cached answer",
  "sources": ["doc1.pdf", "doc2.md"]
}
```

Lookup is linear over `ragcache:index`: each entry's `embedding` is compared to the
query embedding by cosine similarity; the best entry with similarity ≥
`app.cache.similarity-threshold` is returned. All Redis access is best-effort — on any
error the request proceeds without the cache.

## Embeddings

- Model: `all-MiniLM-L6-v2` (sentence-transformers), ONNX, run in-process via DJL.
- Output: `last_hidden_state`; token embeddings are mean-pooled and L2-normalised to a
  single 384-dim vector per text.
- Deterministic for a given input; identical text → identical vector (cosine 1.0),
  which is why repeated questions are exact cache hits.

## HTTP DTOs

### `POST /api/ingest`
Request: `multipart/form-data`, repeatable field `files`.

Response `200`:
```json
{ "totalChunks": 42, "results": [ { "file": "handbook.pdf", "chunks": 39 } ] }
```

### `POST /api/documents`
Source-agnostic ingestion of chunks already in the standard contract (bypasses Tika).
Request: JSON array of chunks.
```json
[
  { "documentId": "manual.pdf", "sectionPath": "Ch2 > Brakes",
    "content": "brake torque is 120Nm", "metadata": {"lang": "en"},
    "aclTags": ["engineering"] },
  { "documentId": "manual.pdf", "content": "error ERR-503-GATEWAY" }
]
```
`sectionPath`→`""`, `metadata`→`{}` and `aclTags`→`["public"]` when omitted.
Response `200`: `{ "stored": 2 }`. Returns `400` if any chunk has a blank `documentId`
or `content`.

### `POST /api/chat`
Request:
```json
{ "question": "string (non-blank)" }
```
Optional header `X-Roles: engineering,support` — a comma-separated list of the caller's
roles. Under the hybrid store this scopes retrieval by RBAC (only `public` chunks plus
those whose `aclTags` intersect the roles are visible). The vector store ignores it.

Response `200` — `ChatAnswer`:
```json
{ "answer": "string", "sources": ["string"], "cached": false }
```

| Field | Type | Notes |
| --- | --- | --- |
| `answer` | string | Output-filtered model answer. |
| `sources` | string[] | Distinct `metadata.source` of retrieved chunks. |
| `cached` | boolean | True when served from the semantic cache. |

Error bodies use Spring's default problem shape:
```json
{ "timestamp": "…", "status": 400, "error": "Bad Request", "path": "/api/chat" }
```

See [API Reference](../user-guide/api-reference.md) for the full status-code matrix.

## Configuration keys (bindings)

Prefixes bound as typed `@ConfigurationProperties`:

| Prefix | Class | Fields |
| --- | --- | --- |
| `app.rag` | `RagProperties` | `topK`, `systemPrompt` |
| `app.retrieval` | `RetrievalProperties` | `mode` (`vector`\|`hybrid`), `rrfK`, `textSearchConfig` |
| `app.cache` | `CacheProperties` | `enabled`, `similarityThreshold`, `ttlSeconds`, `maxEntries` |
| `app.guardrails` | `GuardrailProperties` | `enabled`, `redactPii`, `blockPromptInjection`, `filterOutput`, `bannedOutputPhrases` |
| `app.rate-limit` | `RateLimitProperties` | `enabled`, `requestsPerMinute` |
| `app.ingest` | `IngestProperties` | `maxFileSizeBytes`, `allowedContentTypes` |

Infrastructure keys (`spring.datasource.*`, `spring.data.redis.*`,
`spring.ai.openai.*`, `spring.ai.vectorstore.pgvector.*`,
`spring.ai.embedding.transformer.*`) are standard Spring/Spring AI properties — see
[Configuration](../user-guide/configuration.md).
