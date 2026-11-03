# Technical Documentation

Detailed internals for developers extending or reasoning about the service — written to
be read start-to-finish as a **learning path**, from first principles to implementation.
Suitable for self-study or teaching. For usage, see the
[User Guide](../user-guide/README.md).

### Learning path

1. [Concepts — RAG From the Ground Up](concepts.md) — **start here.** What RAG is and
   why; embeddings, vectors, cosine similarity, chunking, vector databases/HNSW,
   grounding, tokens, semantic caching, guardrails, rate limiting — each with intuition,
   a worked example, and a glossary. No prior ML knowledge assumed.
2. [Architecture](architecture.md) — how those ideas are deployed and connected;
   ingestion/query flows; design decisions.
3. [Data Model](data-model.md) — the exact schemas: pgvector table DDL, Redis key layout,
   DTO/JSON contracts, configuration bindings.
4. [Internals](internals.md) — package & class responsibilities and the core algorithms
   (embeddings, cache, guardrails, rate limiter, metrics), step by step.
5. [Extending](extending.md) — change the LLM or embedding model, swap the vector store,
   add guardrails, tune retrieval/caching, write hermetic tests.

## Stack

| Layer | Technology |
| --- | --- |
| Language / runtime | Java 21, Spring Boot 3.3 |
| AI framework | Spring AI 1.0 (ChatClient, VectorStore, EmbeddingModel abstractions) |
| Embeddings | DJL + ONNX Runtime, `all-MiniLM-L6-v2` (384-dim), in-process |
| Vector store | PostgreSQL 16 + `pgvector` (HNSW, cosine) |
| Cache | Redis 7 (semantic cache) |
| LLM | External OpenAI-compatible API (Groq default) |
| Observability | Micrometer → Prometheus |
| Packaging | Multi-stage Docker image, Helm 3 chart |
