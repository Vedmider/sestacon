# pgvector

## What it is
A PostgreSQL extension adding a `vector` column type and nearest-neighbour search operators,
so [embeddings](embeddings.md) live in the same DB as the rest of the data.

## Why
No separate vector database to run; transactional, familiar ops, and it can combine vector
search with ordinary SQL/full-text in one query — see [hybrid search](hybrid-search-rrf.md).

## How it works
- Store an N-dim `vector` per row (N must match the embedding model).
- Distance operators: `<=>` cosine, `<->` L2, `<#>` inner product.
- **Index** for speed: **HNSW** (graph-based, fast approximate NN) or IVFFlat. `rag-app`
  uses HNSW + cosine.
- Query: `ORDER BY embedding <=> :q LIMIT k` returns the top-k nearest chunks.

Caveats: ANN indexes are approximate (recall tuning); dimension is fixed per column; large
corpora need index tuning.

## In our apps
- [`rag-app`](../apps/rag-app.md): pgvector HNSW/cosine, 384-dim; also the SQL side of
  [hybrid search](hybrid-search-rrf.md).

## Related
[Embeddings](embeddings.md) · [Hybrid search + RRF](hybrid-search-rrf.md) ·
[`rag-app`](../apps/rag-app.md)
