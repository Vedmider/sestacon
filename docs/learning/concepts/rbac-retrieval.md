# RBAC retrieval

## What it is
Filtering retrieved chunks by the caller's roles, so a query only returns content the caller
is allowed to see. Chunks carry `aclTags`; the caller presents roles.

## Why
A shared doc store may hold content of mixed sensitivity; retrieval must not leak restricted
chunks to the wrong caller.

## How it works
- Ingest tags each chunk with `aclTags` (default `public`).
- A chunk is visible if it is `public`, untagged, or its tags intersect the caller's roles.
- In [hybrid search](hybrid-search-rrf.md) this is applied **before** ranking
  (`allowed_roles && caller_roles` in SQL).

**Security caveat (from the `rag-app` review):** roles must come from an **authenticated**
identity. A raw client header (`X-Roles`) with no auth = bypass. Fine for localhost-only;
needs real auth before any shared exposure.

## In our apps
- [`rag-app`](../apps/rag-app.md): `aclTags` + roles in both stores.
- [`code-memory`](../apps/code-memory.md): mostly drops out (single-repo code index).

## Related
[Hybrid search + RRF](hybrid-search-rrf.md) · [`rag-app`](../apps/rag-app.md) ·
[Guardrails](guardrails.md)
