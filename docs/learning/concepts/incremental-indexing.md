# Incremental indexing

## What it is
Keeping the index in sync with changing source by re-indexing **only what changed**, and
**deleting** units for removed/renamed items.

## Why
Code changes continuously; full re-index every edit is wasteful, and a stale index yields
wrong [locators](locator-search.md). But most commits change file *bodies*, not structure —
so most updates can be cheap or skipped.

## How it works
- Key each unit by `path` + **content hash** (`gitBlobHash`) — the [Cursor](../real-world/cursor-indexing.md)
  Merkle-tree idea.
- On a **git hook** (pre/post-commit) or file watcher:
  - file add / rename / delete, or public-signature/class diff → **reindex** that file;
  - body-only change → **skip** (keeps the "mostly no-op" behaviour).
- Delete symbols no longer present; upsert changed ones.

This "structural-change detector" is the trigger that refreshes the static semantic layer
in [`rag-app`](../apps/rag-app.md) and the symbol index in [`code-memory`](../apps/code-memory.md).

## In our apps
- [`code-memory`](../apps/code-memory.md): git-hash incremental sync + delete-by-path (new
  vs `rag-app`'s append-only ingest).
- [`rag-app`](../apps/rag-app.md): re-ingest the static layer on structural change only.

## Related
[Locator search](locator-search.md) · [Redis caching](redis-caching.md) ·
[Cursor indexing](../real-world/cursor-indexing.md) · [`code-memory`](../apps/code-memory.md)
