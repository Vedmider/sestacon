# Tika document parser

## What it is
Apache Tika is a content-extraction library that turns many file formats (PDF, DOCX, HTML,
Markdown, plain text, …) into plain text + metadata. In `rag-app` the `TikaDocumentParser`
adapter wraps it.

## Why
Ingestion must accept heterogeneous documents. Tika gives one API (`detect` + `parse`)
across formats, so the rest of the pipeline sees uniform text regardless of source type.

## How it works
1. Receives the uploaded bytes + declared content type.
2. Auto-detects the real type and selects a parser.
3. Extracts text (and metadata: title, author, …).
4. Hands text to the [chunker](chunking.md) → [embeddings](embeddings.md) →
   [pgvector](pgvector.md).

Caveats: scanned PDFs need OCR (not covered by plain Tika); content-type allow-listing and
size limits should gate it (see `rag-app`'s ingest validation).

## In our apps
- [`rag-app`](../apps/rag-app.md): `TikaDocumentParser` on the `/api/ingest` path.
- [`code-memory`](../apps/code-memory.md): **does not** use Tika — it parses code with an
  AST/tree-sitter parser instead (see [structural ranking](structural-ranking.md)).

## Related
[Chunking](chunking.md) · [`rag-app`](../apps/rag-app.md) ·
[Structural ranking](structural-ranking.md)
