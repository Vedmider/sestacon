# RAG Learning

Knowledge base for our two retrieval apps — **`Sestacon`** (static/doc) and
**`code-memory`** (dynamic/code locator index) — plus the general concepts and real-world
systems they build on.

**Navigation:** every page links to related pages ("Related" at the bottom). Generic
concepts are explained **once** under [`concepts/`](concepts/) — app pages link to them
instead of repeating. See [CONTRIBUTING](CONTRIBUTING.md) for the no-duplication rule.

## Start here
- [Two-app architecture](apps/two-app-architecture.md) — the big picture (Sestacon + code-memory)
- [`Sestacon`](apps/sestacon.md) — static/doc retrieval
- [`code-memory`](apps/code-memory.md) — code locator index

## Concepts (single source of truth)
- [Chunking](concepts/chunking.md)
- [Tika document parser](concepts/tika-document-parser.md)
- [Embeddings (dense vectors)](concepts/embeddings.md)
- [pgvector](concepts/pgvector.md)
- [Hybrid search + RRF](concepts/hybrid-search-rrf.md)
- [Locator search](concepts/locator-search.md)
- [Structural ranking](concepts/structural-ranking.md)
- [Incremental indexing](concepts/incremental-indexing.md)
- [RBAC retrieval](concepts/rbac-retrieval.md)
- [Guardrails](concepts/guardrails.md)
- [Redis caching (hot tier)](concepts/redis-caching.md)
- [Retrieve-only vs chat](concepts/retrieve-only-vs-chat.md)

## Real-world systems
- [Aider repo-map](real-world/aider-repomap.md)
- [Cursor indexing](real-world/cursor-indexing.md)
- [Continue.dev](real-world/continue-dev.md)
- [Sourcegraph Cody](real-world/sourcegraph-cody.md)
- [GitHub Copilot @workspace](real-world/copilot-workspace.md)
- [LlamaIndex router](real-world/llamaindex-router.md)
- [Comparison table (ours vs them)](real-world/comparison-table.md)

## Decisions (ADRs)
- [ADR-001 Two-app split](decisions/adr-001-two-app-split.md)
- [ADR-002 Locators for code](decisions/adr-002-locators-for-code.md)
- [ADR-003 Structural vs dense](decisions/adr-003-structural-vs-dense.md)
- [ADR-004 Retrieve-only](decisions/adr-004-retrieve-only.md)
