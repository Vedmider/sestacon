# RAG App Documentation

Documentation is split into two tracks.

## 📘 [User Guide](user-guide/README.md) — how to use the app
For people running and consuming the service.

- [Getting Started](user-guide/getting-started.md) — run locally, build the image, first ingest + chat
- [API Reference](user-guide/api-reference.md) — endpoints, request/response, status codes
- [Configuration](user-guide/configuration.md) — all properties and environment variables
- [Limits & Dimensioning](user-guide/limits.md) — payload/rate/vector limits, AI-provider restrictions, what is not enforced
- [Deployment](user-guide/deployment.md) — Helm chart, Kubernetes, secrets, scaling
- [Operations](user-guide/operations.md) — metrics, caching, guardrails, troubleshooting

## 🔧 [Technical Docs](technical/README.md) — how it works in detail
For people extending or reasoning about the internals — written as a ground-up
learning path, suitable for self-study or teaching.

- [Concepts — RAG From the Ground Up](technical/concepts.md) — **start here**: the ideas, from first principles
- [Architecture](technical/architecture.md) — components, request flows, design decisions
- [Data Model](technical/data-model.md) — pgvector schema, Redis keys, DTO/JSON schemas
- [Internals](technical/internals.md) — package/class design, algorithms, pipeline step-by-step
- [Extending](technical/extending.md) — swap the LLM/embedding model, add guardrails, tune retrieval

## Other
- [Implementation Plan](rag-app-plan.md) — original design/plan document.
- [Learning Materials](learning/README.md) — knowledge base on the concepts, the two-app
  architecture, decisions (ADRs), and real-world systems (Aider, Cursor, Cody, …) these apps build on.

---

### What this service does
It answers questions grounded in your own documents (Retrieval-Augmented Generation):
you upload documents (`/api/ingest`), which are parsed, chunked, embedded locally, and
stored in pgvector; you ask a question (`/api/chat`), and the service retrieves the most
relevant chunks, builds a grounded prompt, and calls an external LLM — returning the
answer plus its sources. It adds a semantic cache, guardrails, rate limiting, and metrics,
and ships as one Helm chart (app + PostgreSQL/pgvector + Redis).
