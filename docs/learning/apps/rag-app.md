# App: `rag-app` (static / doc retrieval)

## Purpose
Store and retrieve **stable knowledge** — requirements, skills, coding conventions,
architecture, class-purpose summaries — for grounding kiro during development. Spring Boot
(Java 21), hexagonal architecture, Spring AI + [pgvector](../concepts/pgvector.md),
[Redis](../concepts/redis-caching.md) hot tier.

## Architecture decisions
- **Hexagonal** (domain ports + in/out adapters) → swap AI provider / store without touching
  business logic.
- **Returns chunk text** (docs are stable, so embedded text is safe — contrast
  [`code-memory`](code-memory.md) locators).
- **Hot tier in Redis** for the slow-changing static layer; invalidate on
  [structural change](../concepts/incremental-indexing.md) only.
- **Localhost-only** until the auth gap is fixed (see [RBAC](../concepts/rbac-retrieval.md)).

## E2E API / flow
- `POST /api/ingest` — upload files → [Tika parse](../concepts/tika-document-parser.md) →
  [chunk](../concepts/chunking.md) → [embed](../concepts/embeddings.md) →
  store ([pgvector](../concepts/pgvector.md)).
- `POST /api/documents` — ingest **pre-chunked** JSON (skip Tika).
- `POST /api/chat` — `embed → search → [guardrails] → LLM → answer`
  (the doc-Q&A path — see [retrieve-only vs chat](../concepts/retrieve-only-vs-chat.md)).
- `POST /api/retrieve` *(Task 1, new)* — `embed → search → return chunks` (**no LLM**);
  this is what kiro calls.

## Components (explained once in concepts)
[Chunking](../concepts/chunking.md) · [Tika parser](../concepts/tika-document-parser.md) ·
[Embeddings](../concepts/embeddings.md) · [pgvector](../concepts/pgvector.md) ·
[Hybrid + RRF](../concepts/hybrid-search-rrf.md) ·
[RBAC retrieval](../concepts/rbac-retrieval.md) · [Guardrails](../concepts/guardrails.md) ·
[Redis caching](../concepts/redis-caching.md).

## Retrieval modes
`vector` (dense only) or `hybrid` ([vector + full-text + RBAC + RRF](../concepts/hybrid-search-rrf.md)).

## Related
[`code-memory`](code-memory.md) · [Two-app architecture](two-app-architecture.md) ·
[Retrieve-only vs chat](../concepts/retrieve-only-vs-chat.md) ·
[Continue.dev](../real-world/continue-dev.md)
