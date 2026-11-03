# User Guide

How to run, configure, deploy, and operate the RAG service. For internal mechanics
and schemas, see the [Technical Docs](../technical/README.md).

1. [Getting Started](getting-started.md) — prerequisites, build/test, run locally, first ingest + chat, build the image.
2. [Local Development](local-development.md) — the incremental dev loop: build → run → test the API → stop (ephemeral, auto-cleaned).
3. [API Reference](api-reference.md) — `/api/ingest`, `/api/documents` and `/api/chat`: requests, responses, status codes; actuator endpoints.
4. [Configuration](configuration.md) — every config property and environment variable.
5. [Limits & Dimensioning](limits.md) — payload/upload sizes, rate limits, vector/retrieval constraints, AI-provider restrictions, and what is not enforced.
6. [Deployment](deployment.md) — Helm install, LLM key secret, image overrides, full values reference, production checklist.
7. [Operations](operations.md) — metrics (with PromQL), health probes, cache/guardrails/rate-limit behaviour, troubleshooting.
