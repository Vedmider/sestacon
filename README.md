# RAG App — Spring AI + pgvector + Helm/K8s

Production-ready Retrieval-Augmented Generation service. Upload your documents, then
ask questions answered **grounded in those documents** with source attribution.

- **Java 21 · Spring Boot 3.3 · Spring AI 1.0**
- **Local ONNX embeddings** (all-MiniLM-L6-v2, 384-dim) — document content stays in-cluster
- **pgvector** vector store · **Redis** semantic cache
- **External OpenAI-compatible LLM** for generation (Groq by default, pluggable)
- **Production hardening**: PII redaction + prompt-injection guardrails, per-client
  rate limiting, Prometheus metrics, non-root/read-only containers
- **One Helm chart** deploys the app + PostgreSQL/pgvector + Redis

## Documentation

Full documentation is in [`docs/`](docs/README.md), split into two tracks:

**User guide** (how to use)
- [Getting Started](docs/user-guide/getting-started.md) — run locally, build the image, first ingest + chat
- [Local Development](docs/user-guide/local-development.md) — incremental dev loop (build → run → test API → stop, auto-cleaned)
- [API Reference](docs/user-guide/api-reference.md) — endpoints, request/response, status codes
- [Configuration](docs/user-guide/configuration.md) — all properties and environment variables
- [Deployment](docs/user-guide/deployment.md) — Helm chart, Kubernetes, secrets, scaling
- [Operations](docs/user-guide/operations.md) — metrics, caching, guardrails, troubleshooting

**Technical docs** (how it works, ground-up learning path)
- [Concepts](docs/technical/concepts.md) — RAG from first principles
- [Architecture](docs/technical/architecture.md) · [Data Model](docs/technical/data-model.md) · [Internals](docs/technical/internals.md) · [Extending](docs/technical/extending.md)

## Quick start

```bash
source env.sh && mvn -B verify          # build + test (unit + Testcontainers)

# run locally against Docker Postgres(pgvector) + Redis
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_DB=ragdb -e POSTGRES_USER=rag -e POSTGRES_PASSWORD=rag pgvector/pgvector:pg16
docker run -d --name redis -p 6379:6379 redis:7-alpine
OPENAI_API_KEY=<your-key> mvn spring-boot:run

# ingest a doc, then ask about it
echo "The Eiffel Tower is 330 metres tall." > fact.txt
curl -F files=@fact.txt http://localhost:8080/api/ingest
curl -XPOST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"How tall is the Eiffel Tower?"}'
```

## Deploy

```bash
helm install rag ./charts/rag-app -n rag --create-namespace
# then supply a real LLM key (see docs/deployment.md)
```

See [Deployment](docs/deployment.md) for the full values reference and production
checklist.

## Repository layout

```
src/main/java/com/example/rag/   app code (ingest, chat, cache, guardrail, ratelimit, metrics)
src/main/resources/              application.yml + vendored ONNX model
src/test/java/com/example/rag/   unit (*Test) and Testcontainers integration (*IT) tests
charts/rag-app/                  Helm chart (app + PostgreSQL/pgvector + Redis)
docs/                            documentation (this folder)
scripts/                         build/smoke/e2e helper scripts
Dockerfile                       multi-stage, offline embeddings, non-root runtime
```


## Author

Created by **Andrii Lisovets** (andrii.lisovets@gmail.com).

## License

Released under the [MIT License](LICENSE) — © 2026 Andrii Lisovets. Free to use, modify,
and distribute; see [`LICENSE`](LICENSE) for the full text.
