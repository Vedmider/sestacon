# Comparison — our design vs real-world systems

How our two apps map onto production tools. Details evolve; architecture shapes are stable.

| Tool | Static KB | Code store | Code = locator or content? | Sync | Maps to our… |
|---|---|---|---|---|---|
| [Aider](aider-repomap.md) | — | tree-sitter repo map (symbols+signatures) | **Locator** — reads full files only when editing | git-aware, PageRank | [`code-memory`](../apps/code-memory.md) locator + read-fresh |
| [Cursor](cursor-indexing.md) | `@docs` | embedding index, Merkle hashing | mostly content, code local | **Incremental** (Merkle diff) | [incremental indexing](../concepts/incremental-indexing.md) + docs/code split |
| [Continue.dev](continue-dev.md) | `@docs` | `@codebase` local embeddings | content chunks | local refresh | our **two-source split** (most literal) |
| [Sourcegraph Cody](sourcegraph-cody.md) | — | code graph (SCIP) + keyword + embeddings | **locator-leaning** | server-indexed | [structural ranking](../concepts/structural-ranking.md) |
| [Copilot @workspace](copilot-workspace.md) | — | blended embeddings+lexical | content | IDE-maintained | single-store (what we improve on) |
| [LlamaIndex router](llamaindex-router.md) | index A | index B + router | either | manual | our **two-store + router** |
| Claude Code / kiro | — | no index — grep/read on demand | **locator by search** | always fresh | our **read-fresh bytes** step |

## Takeaways
- **Locators win for code** — Aider/Sourcegraph; pure-embedding code retrieval is the losing
  trend. → [`code-memory`](../apps/code-memory.md), [ADR-002](../decisions/adr-002-locators-for-code.md).
- **Static-vs-dynamic split** is mainstream (Continue `@docs`/`@codebase`, Cursor `@docs`).
  → [Two-app architecture](../apps/two-app-architecture.md), [ADR-001](../decisions/adr-001-two-app-split.md).
- Most tools use **one engine with tags**, not two deployments — our split buys ops/RBAC
  cleanliness at some infra cost.

## Related
[Two-app architecture](../apps/two-app-architecture.md) ·
[Structural ranking](../concepts/structural-ranking.md) ·
[Locator search](../concepts/locator-search.md)
