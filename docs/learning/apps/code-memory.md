# App: `code-memory` (dynamic / code locator index)

## Purpose
Give kiro a fast, always-fresh **map of the code** for refactoring: return
[**locators**](../concepts/locator-search.md) (`path, symbol, lines, signature, score`), not
code text — kiro reads the live bytes. Optimized for **continuous code change**.

## Architecture decisions
- **Locators, not content** — avoids refactoring against stale copies; token-cheap. See
  [ADR-002](../decisions/adr-002-locators-for-code.md), [Aider](../real-world/aider-repomap.md).
- **Symbol-aware indexing** (tree-sitter), one unit per function/class — see
  [chunking](../concepts/chunking.md).
- **[Structural ranking](../concepts/structural-ranking.md) first**, dense optional — see
  [ADR-003](../decisions/adr-003-structural-vs-dense.md).
- **[Incremental indexing](../concepts/incremental-indexing.md)** with update + delete
  (new capability vs `Sestacon`'s append-only ingest).
- RBAC mostly drops out (single-repo index).

## How it works (flow)
1. **Indexer (pull):** reads the working tree / git (sidecar or CLI) — not external push.
2. **Parse** with tree-sitter → symbols + signatures + references.
3. **Store** locator records keyed by `path`+`gitBlobHash`; optional symbol/call graph.
4. **Sync:** git hook / watcher → reindex changed files, delete removed/renamed symbols
   (body-only change → skip).
5. `POST /api/retrieve` → ranked [locators](../concepts/locator-search.md)
   (+ optional "who calls / is called by" = refactor blast radius).

## Shared with `Sestacon`
Service skeleton, hexagonal ports, Redis, embedding provider (if dense kept). It's a new
**adapter set** (parser, store projection, indexer), not built from scratch.

## MVP → next
MVP: tree-sitter symbol index + git-hash incremental sync + `/retrieve` returning
path+symbol+line range. **Next:** call/usage graph.

## Related
[`Sestacon`](sestacon.md) · [Two-app architecture](two-app-architecture.md) ·
[Locator search](../concepts/locator-search.md) ·
[Structural ranking](../concepts/structural-ranking.md) ·
[Aider repo-map](../real-world/aider-repomap.md)
