# ADR-002 — Use locators (not embedded code) in `code-memory`

**Status:** accepted

## Context
Embedding code bodies and returning them risks (a) refactoring against a **stale** copy and
(b) high token cost.

## Decision
[`code-memory`](../apps/code-memory.md) returns [**locators**](../concepts/locator-search.md)
(`path, symbol, lines, signature, score`); kiro reads the **current** bytes for truth.

## Rationale
- Correctness: live file is the source of truth, never the index.
- Tokens: locators are tiny; fetch only pinpointed slices.
- Prior art: [Aider repo-map](../real-world/aider-repomap.md),
  [Sourcegraph Cody](../real-world/sourcegraph-cody.md).

## Trade-off
Requires a round-trip (retrieve → read). Negligible vs the quality/token win.

## Related
[Locator search](../concepts/locator-search.md) · [ADR-003](adr-003-structural-vs-dense.md)
