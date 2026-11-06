# Sourcegraph — Cody

## What it is
Cody is Sourcegraph's AI assistant, built on their code-search platform and **code graph**
(SCIP precise indexing).

## Approach
- Retrieval blends **keyword/structural search + the code graph** with (historically)
  embeddings; Sourcegraph publicly de-emphasised pure embeddings in favour of search/graph.
- Precise cross-references (defs/refs) come from SCIP indexes.

## What we borrow
Validates **[structural ranking](../concepts/structural-ranking.md)** and an optional
**call/usage graph** in [`code-memory`](../apps/code-memory.md) over pure dense vectors —
especially for "who calls this" blast-radius queries.

## Related
[Structural ranking](../concepts/structural-ranking.md) ·
[Locator search](../concepts/locator-search.md) · [Comparison table](comparison-table.md)
