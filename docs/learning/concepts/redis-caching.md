# Redis caching (hot tier)

## What it is
Using Redis (in-memory, TTL) as a fast tier: a **semantic answer cache** and the **hot store
for stable, high-read content** (the static semantic layer).

## Why
RAM-vs-disk tiering: keep the small, slow-changing, frequently-read data hot (Redis) and the
authoritative/volatile data on the durable tier (PostgreSQL). Cheap to serve, big token
leverage.

## How it works
- **Semantic cache:** key a query by its [embedding](embeddings.md); a near-duplicate
  question (cosine ≥ threshold) returns the cached answer — role-aware
  ([RBAC](rbac-retrieval.md)), with TTL and max-entries.
- **Hot static layer:** repo structure / architecture / class-purpose summaries live in
  Redis because they change slowly; invalidate on **structural change** only
  ([incremental indexing](incremental-indexing.md)).

Caveat: Redis is then a critical dependency (lock/cache/bus in some designs) — prefer HA.

## In our apps
- [`Sestacon`](../apps/sestacon.md): `RedisAnswerCache` + hot static tier.

## Related
[Incremental indexing](incremental-indexing.md) · [Embeddings](embeddings.md) ·
[Two-app architecture](../apps/two-app-architecture.md)
