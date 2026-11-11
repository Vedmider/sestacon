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

## What it's for / what questions it answers

Sestacon is the **document & knowledge layer**: upload prose (product docs, runbooks, design notes,
skills, agent definitions, conventions) and ask natural-language questions answered *grounded in
those documents*, with source attribution. It is the "what / why / which" brain — not a code index.

Use it to answer:
- "How does rate limiting work in this product?" → the relevant doc/runbook passage + source.
- "Which agent decomposes a Jira epic into subtasks?" → the matching agent/skill definition.
- "What's our convention for PII redaction / retry / logging?" → the governing doc section.
- "Explain the caching strategy." → the design-note chunk that describes it.
- "Answer this question using only our docs." → RAG answer (via the chat path) or raw grounded
  chunks (via the retrieve-only path, token-cheap for an agent that reasons itself).

Not the right tool for: "where is method X defined", exact code spans, or uncommitted edits — that is
code-memory (fuzzy code recall) and Dylext (exact code + live git). See
`Dylext/docs/ARCHITECTURE-THREE-APPS.md` for how the three compose.

## Run locally (Docker)

Clone, then one script brings up the whole stack (app + pgvector + Redis) as throwaway
containers:

```bash
export OPENAI_API_KEY=<your-key>     # only needed for /api/chat answers
bash scripts/dev-local.sh up         # downloads the model if missing, builds, starts on :18080
```

Test it (curl or Postman against `http://localhost:18080`):
```bash
curl -F "files=@notes.md;type=text/markdown" http://localhost:18080/api/ingest
curl -XPOST http://localhost:18080/api/chat -H 'Content-Type: application/json' \
     -d '{"question":"what do my notes say?"}'
bash scripts/dev-local.sh down       # stop + wipe everything
```

- **Defaults (no config):** local MiniLM embeddings (384-dim) · Groq LLM `openai/gpt-oss-20b` · `vector` retrieval · Redis cache · guardrails on. The only thing you provide is `OPENAI_API_KEY`.
- The embedding model (~87 MB) is **not** in git — the script downloads it on first run
  (or run `scripts/fetch-model.sh` yourself); the image is built with it baked in.
- **Tuning (other options):** switch LLM, embeddings, or retrieval via env or
  `scripts/config.sh` — e.g. `RETRIEVAL_MODE=hybrid`, `EMBEDDING_PROVIDER=openai`, or a
  local Ollama LLM with `LLM_MODEL=llama3.1 OPENAI_BASE_URL=http://host.docker.internal:11434`.
  See [Local Development](docs/user-guide/local-development.md).

## Metadata enrichment & filtered retrieval

At ingest, every chunk is enriched with derived metadata — **rule-based, no LLM** (stays
provider-agnostic): `docType` (from the document id's extension), `lang`, `tags` (keyword content
classification — runbook/api/security/config/architecture), `sourceSystem` (provenance, from an
optional `sourceSystem` ingest param), and `ingestedAt`. Caller-supplied metadata keys are preserved.

Retrieval accepts an optional exact-match metadata filter, combined with RBAC. Example:

```bash
curl -XPOST http://localhost:18080/api/retrieve -H 'Content-Type: application/json' \
     -H 'X-Roles: engineering' \
     -d '{"query":"rate limiting","metadata":{"sourceSystem":"jira","docType":"md"}}'
```

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
helm install rag ./charts/sestacon -n rag --create-namespace
# then supply a real LLM key (see docs/deployment.md)
```

See [Deployment](docs/deployment.md) for the full values reference and production
checklist.

## Repository layout

```
src/main/java/com/example/rag/   app code (ingest, chat, cache, guardrail, ratelimit, metrics)
src/main/resources/              application.yml + vendored ONNX model
src/test/java/com/example/rag/   unit (*Test) and Testcontainers integration (*IT) tests
charts/sestacon/                  Helm chart (app + PostgreSQL/pgvector + Redis)
docs/                            documentation (this folder)
scripts/                         build/smoke/e2e helper scripts
Dockerfile                       multi-stage, offline embeddings, non-root runtime
```


## Author

Created by **Andrii Lisovets** (andrii.lisovets@gmail.com).

## License

Released under the [MIT License](LICENSE) — © 2026 Andrii Lisovets. Free to use, modify,
and distribute; see [`LICENSE`](LICENSE) for the full text.
