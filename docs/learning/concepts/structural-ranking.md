# Structural ranking

## What it is
Ranking code by its **structure** — a symbol/call graph (who defines, calls, imports what) —
rather than by [dense vector](embeddings.md) similarity. Often PageRank-style over the graph.

## Why
- Code relevance is structural: the functions that **call** or are **called by** your target
  are what matter for a refactor (the "blast radius").
- Deterministic, no embedding model, no vector **staleness**.
- Industry trend: Sourcegraph moved toward graph/keyword; [Aider](../real-world/aider-repomap.md)
  never embedded code — it ranks a tree-sitter symbol map.

## How it works
1. Parse files with **tree-sitter** → symbols + signatures + references.
2. Build a graph: nodes = symbols/files, edges = calls/imports/defs.
3. Rank by graph centrality (e.g. PageRank) biased toward the query/mentioned files.
4. Return the top symbols as [locators](locator-search.md).

Pair with dense embeddings only for fuzzy natural-language queries ("code about retries").

## In our apps
- [`code-memory`](../apps/code-memory.md): **structural-first**, dense optional; optional
  call/usage graph returns the refactor blast radius.

## Related
[Locator search](locator-search.md) · [Chunking](chunking.md) ·
[Embeddings](embeddings.md) · [Aider repo-map](../real-world/aider-repomap.md) ·
[ADR-003 Structural vs dense](../decisions/adr-003-structural-vs-dense.md)
