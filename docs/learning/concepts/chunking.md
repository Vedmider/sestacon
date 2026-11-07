# Chunking

## What it is
Splitting a source document (or source file) into smaller units ("chunks") that are indexed
and retrieved independently.

## Why
- An LLM / retriever works on bounded pieces; whole documents are too big and dilute
  relevance.
- Retrieval returns the few chunks that matter → grounded answers and
  [fewer tokens](retrieve-only-vs-chat.md).
- Chunk **granularity decides precision**: too big = noisy/expensive, too small = loses
  context.

## How it works
1. Parse the raw input to text/structure (for files, see
   [Tika document parser](tika-document-parser.md); for code, an AST parser).
2. Split by a strategy:
   - **Fixed-size / sliding window** (N tokens, overlap) — generic prose.
   - **Structure-aware** — by heading/section/paragraph (docs) or by **symbol**
     (function/class) for code — see [structural ranking](structural-ranking.md).
3. Attach metadata (source, section, acl tags, for code: path+line span).
4. Embed each chunk ([embeddings](embeddings.md)) and store ([pgvector](pgvector.md)).

Rule of thumb: chunk at the unit a user would want returned — one rule/skill per chunk for
conventions; one symbol per chunk for code.

## In our apps
- [`Sestacon`](../apps/sestacon.md): prose chunking of docs/skills/conventions.
- [`code-memory`](../apps/code-memory.md): **symbol-aware** chunking (one function/class),
  storing [locators](locator-search.md) not bodies.

## Related
[Tika document parser](tika-document-parser.md) · [Embeddings](embeddings.md) ·
[Locator search](locator-search.md) · [Structural ranking](structural-ranking.md) ·
[`Sestacon`](../apps/sestacon.md)
