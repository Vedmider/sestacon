# Continue.dev

## What it is
Open-source IDE assistant with explicit context providers, notably **`@docs`** (static
documentation) and **`@codebase`** (local code retrieval).

## Approach
- `@codebase`: local embeddings index over the repo, retrieved per query.
- `@docs`: ingest external/product docs as a separate, stable source.
- The two are distinct providers you compose in a prompt.

## What we borrow
The most **literal match** to our design: a **static doc source** +
a **dynamic code source** as separate providers — exactly
[`Sestacon`](../apps/sestacon.md) + [`code-memory`](../apps/code-memory.md).

## Related
[Two-app architecture](../apps/two-app-architecture.md) ·
[`Sestacon`](../apps/sestacon.md) · [Comparison table](comparison-table.md)
