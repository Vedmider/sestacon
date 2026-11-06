# Locator search

## What it is
Retrieval that returns **where** relevant code is — `path, symbol, startLine, endLine,
signature, score` — instead of the code text itself. The agent then reads the **current**
bytes from disk.

## Why
- **Correctness:** never refactor against an embedded (possibly stale) copy — read the live
  file. This is the key reason [`code-memory`](../apps/code-memory.md) uses locators.
- **Tokens:** a locator is tiny; you fetch only the pinpointed slices, not whole files/dirs.
- It mirrors the most token-efficient real tools — see [Aider repo-map](../real-world/aider-repomap.md).

## How it works
1. Index code by **symbol** ([chunking](chunking.md) + AST/tree-sitter).
2. Store locator metadata keyed by `path`+`gitBlobHash`
   ([incremental indexing](incremental-indexing.md)).
3. `/retrieve` ranks symbols ([structural ranking](structural-ranking.md) or
   [dense](embeddings.md)) and returns locators.
4. Agent (kiro) reads the exact lines live → truth.

## In our apps
- [`code-memory`](../apps/code-memory.md): locator projection is the whole point.
- [`rag-app`](../apps/rag-app.md): returns **text** chunks (docs are stable, so text is fine);
  same `/retrieve` endpoint shape, different projection.

## Related
[Structural ranking](structural-ranking.md) · [Incremental indexing](incremental-indexing.md) ·
[Aider repo-map](../real-world/aider-repomap.md) · [`code-memory`](../apps/code-memory.md)
