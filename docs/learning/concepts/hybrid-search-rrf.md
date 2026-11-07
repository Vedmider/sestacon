# Hybrid search + RRF

## What it is
Combining two rankings — **dense vector** similarity ([embeddings](embeddings.md)/
[pgvector](pgvector.md)) and **lexical full-text** (PostgreSQL `tsvector`/`tsquery`) — and
fusing them with **Reciprocal Rank Fusion (RRF)**.

## Why
Vectors catch meaning but miss exact tokens (error codes, identifiers); full-text catches
exact terms but misses paraphrase. Fusing both beats either alone.

## How it works
1. Run the vector query → ranked list A.
2. Run the full-text query (`websearch_to_tsquery`) → ranked list B.
3. **RRF**: each item scores `Σ 1/(k + rank_in_list)` across lists (k≈60); sort by summed
   score. Rank-based, so the two score scales don't need normalising.
4. Apply [RBAC](rbac-retrieval.md) filtering **before** ranking.

In `Sestacon` this is one SQL round-trip (`app.retrieval.mode=hybrid`) using a `FULL OUTER
JOIN` of the vec and txt CTEs.

## In our apps
- [`Sestacon`](../apps/sestacon.md): `HybridPgDocumentStore` (vector + tsvector + RBAC + RRF).

## Related
[pgvector](pgvector.md) · [Embeddings](embeddings.md) · [RBAC retrieval](rbac-retrieval.md) ·
[`Sestacon`](../apps/sestacon.md)
