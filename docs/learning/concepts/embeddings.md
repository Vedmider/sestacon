# Embeddings (dense vectors)

## What it is
A model that maps text to a fixed-length vector of floats so that semantically similar texts
land close together (by cosine distance). "Dense" = every dimension carries signal
(contrast sparse/lexical like BM25).

## Why
Lets retrieval find chunks by **meaning**, not exact words ("how do I revert" ≈ "rollback").

## How it works
1. A chunk/query is run through an embedding model → vector (e.g. 384-dim for
   all-MiniLM-L6-v2).
2. Vectors are stored in [pgvector](pgvector.md); search = nearest neighbours to the query
   vector.
3. The model+dimension must match the stored column dimension.

Trade-offs: great for fuzzy natural-language match; **weak/brittle for code** (identifiers,
structure) and goes **stale** if the source changes but the vector isn't re-embedded — which
is why [`code-memory`](../apps/code-memory.md) leans to [structural ranking](structural-ranking.md).

## In our apps
- [`Sestacon`](../apps/sestacon.md): dense embeddings (local ONNX MiniLM or OpenAI-compatible).
- [`code-memory`](../apps/code-memory.md): dense is **optional**; structural preferred.

## Related
[pgvector](pgvector.md) · [Hybrid search + RRF](hybrid-search-rrf.md) ·
[Structural ranking](structural-ranking.md) · [Chunking](chunking.md)
