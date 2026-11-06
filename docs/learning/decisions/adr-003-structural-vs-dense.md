# ADR-003 — Structural ranking first, dense optional (`code-memory`)

**Status:** proposed (decide before Task 2 build)

## Context
Ranking code retrieval: [dense vectors](../concepts/embeddings.md) vs
[structural ranking](../concepts/structural-ranking.md) (symbol/call graph).

## Decision (proposed)
**Structural-first**, dense optional for fuzzy NL queries. Optionally add a **call/usage
graph** for refactor blast-radius.

## Rationale
- Code relevance is structural (callers/callees), deterministic, no vector staleness.
- Dense is brittle for identifiers/structure; useful only for "code about X" phrasing.
- Prior art: [Aider](../real-world/aider-repomap.md) (no code embeddings),
  [Sourcegraph](../real-world/sourcegraph-cody.md) (graph/keyword).

## Open question
Is dense worth keeping at all for NL developer queries, or is lexical + structural enough?

## Related
[Structural ranking](../concepts/structural-ranking.md) · [Embeddings](../concepts/embeddings.md) ·
[`code-memory`](../apps/code-memory.md)
