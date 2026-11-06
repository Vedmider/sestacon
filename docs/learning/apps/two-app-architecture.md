# Two-app architecture: `rag-app` + `code-memory`

## Use case
Ask kiro to refactor code; kiro fetches conventions + code context from the two apps to
**boost quality and cut token usage** — without either app invoking an LLM
([retrieve-only](../concepts/retrieve-only-vs-chat.md)).

## Split by volatility + role (RAM vs disk)
- **[`rag-app`](rag-app.md)** = hot/derived tier. Stable summaries (conventions, architecture,
  class-purpose). Returns **text**. [Redis](../concepts/redis-caching.md)-cached; reindex on
  [structural change](../concepts/incremental-indexing.md) only.
- **[`code-memory`](code-memory.md)** = authoritative/volatile code index. Returns
  **[locators](../concepts/locator-search.md)**; kiro reads fresh bytes = truth.

## Schema (inbound / outbound)
```
             ┌──────────────── kiro-cli (only LLM brain) ───────────────┐
 "refactor X"│ 1 query rag-app     → convention/skill CHUNKS (text)      │
             │ 2 query code-memory → LOCATORS (path+symbol+lines)        │
             │ 3 read fresh slices with file tool                        │
             │ 4 refactor + edit + run tests                             │
             └──────┬──────────────────────────────┬────────────────────┘
                    ▼ /retrieve (no LLM)            ▼ /retrieve (no LLM)
              ┌───────────────┐             ┌────────────────────┐
              │ rag-app       │             │ code-memory        │
              │ pgvector+Redis│             │ tree-sitter index  │
              └───────────────┘             └────────────────────┘
          ingest on doc/CI change        incremental git-hash sync
```

## Golden rules
- Retrieve-only; **kiro is the only LLM**.
- Static = **hints**, cite-backed; dynamic + fresh read = **truth**.
- Cache-invalidation is the whole game → [incremental indexing](../concepts/incremental-indexing.md).
- Keep both on **localhost** (auth gap — [RBAC](../concepts/rbac-retrieval.md)).

## Prior art
[Continue.dev](../real-world/continue-dev.md) (docs/code split) ·
[Aider](../real-world/aider-repomap.md) (locators) ·
[Cursor](../real-world/cursor-indexing.md) (incremental) ·
[LlamaIndex router](../real-world/llamaindex-router.md) (two-store) ·
[full comparison](../real-world/comparison-table.md).

## Related
[`rag-app`](rag-app.md) · [`code-memory`](code-memory.md) ·
[ADR-001](../decisions/adr-001-two-app-split.md) · [ADR-004](../decisions/adr-004-retrieve-only.md)
