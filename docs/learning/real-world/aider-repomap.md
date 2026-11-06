# Aider — repo-map

## What it is
Aider (open-source AI pair-programmer) builds a concise **repo map**: a tree-sitter-derived
list of files with their key **symbols, signatures, and critical definition lines** — not
full file bodies.

## Approach
- tree-sitter extracts symbols; the map is **ranked** (graph/PageRank over references) so the
  most relevant symbols for the current request are included within a token budget.
- The map is sent to the LLM as context; **full files are read only when actually editing**.
- No code embeddings — pure [structural ranking](../concepts/structural-ranking.md).

## What we borrow
This is the archetype for [`code-memory`](../apps/code-memory.md): **[locators](../concepts/locator-search.md),
not embedded bodies**, and read-fresh-bytes for truth. Our strongest validation that
locators are the token-efficient, correctness-preserving choice.

## Related
[Locator search](../concepts/locator-search.md) ·
[Structural ranking](../concepts/structural-ranking.md) ·
[`code-memory`](../apps/code-memory.md) · [Comparison table](comparison-table.md)
