# ADR-001 — Split into two apps (`Sestacon` + `code-memory`)

**Status:** accepted

## Context
Refactoring needs two kinds of context: stable knowledge (conventions, architecture) and
live code. They differ in volatility, optimal retrieval model, and flow.

## Decision
Run **two apps**: [`Sestacon`](../apps/sestacon.md) (static, text chunks, Redis hot tier) and
[`code-memory`](../apps/code-memory.md) (dynamic, [locators](../concepts/locator-search.md),
git-sync).

## Rationale
- Different optimal flows: stable/cacheable vs continuously re-indexed (RAM-vs-disk tiering,
  see [Redis caching](../concepts/redis-caching.md), [incremental indexing](../concepts/incremental-indexing.md)).
- Clean ops/scaling/RBAC; the static tier can be shared across repos/teams.
- Prior art: [Continue.dev](../real-world/continue-dev.md), [Cursor](../real-world/cursor-indexing.md) docs/code split.

## Trade-off
Most tools use one engine with source tags, not two deployments. Extra infra justified when
the static KB is shared and the flows truly diverge.

## Related
[Two-app architecture](../apps/two-app-architecture.md) · [ADR-002](adr-002-locators-for-code.md)
