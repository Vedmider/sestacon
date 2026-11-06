# Cursor — codebase indexing

## What it is
The Cursor editor indexes your codebase for retrieval, and supports `@docs` (indexed
documentation) alongside `@codebase`.

## Approach
- Computes embeddings over code chunks; code stays local, embeddings/obfuscated chunks are
  synced.
- **Merkle-tree file hashing** → only changed files are re-indexed
  ([incremental indexing](../concepts/incremental-indexing.md)).
- Explicit split between **docs** (static) and **codebase** (dynamic) context sources.

## What we borrow
- The **incremental re-index** model (hash-diff) for [`code-memory`](../apps/code-memory.md).
- The **docs-vs-code** separation that our two-app split formalises.

## Related
[Incremental indexing](../concepts/incremental-indexing.md) ·
[Two-app architecture](../apps/two-app-architecture.md) · [Comparison table](comparison-table.md)
