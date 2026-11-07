# Local Development & End-to-End Testing

This is a Kubernetes-oriented app, but you can run and test the **whole stack locally**
during incremental development. The `scripts/dev-local.sh` helper runs the app plus
PostgreSQL/pgvector and Redis as throwaway Docker containers, lets you exercise the API,
and wipes everything on stop.

## Quick start (clone → up → test)

```bash
git clone <repo-url> && cd Sestacon
export OPENAI_API_KEY=<your-key>        # only needed for /api/chat answers
bash scripts/dev-local.sh up            # downloads the model if missing, builds, starts
```
App is now on **http://localhost:18080**. Hit it with curl or Postman (see below), then:
```bash
bash scripts/dev-local.sh down          # stop + wipe everything
```
Or run the whole thing end-to-end in one shot: `bash scripts/dev-local.sh all`.

**Key points**
- **One script does it all** — it downloads the embedding model the first time (or run
  `scripts/fetch-model.sh` yourself beforehand), **builds the image with those files baked
  in** (the build expects them present), and starts app + pgvector + Redis as throwaway
  containers.
- **No large files in git** — the model (~87 MB) and native cache are fetched on demand,
  never committed.
- **Ephemeral** — `down` wipes all data (DB, cache, uploads); each `up` starts clean.
- **Configurable** — pick embeddings / LLM / retrieval via env or `scripts/config.sh`,
  the same knobs as Helm (see [Configuring AI](#configuring-ai--embeddings--llm--retrieval)).

## What runs by default (zero config)

You set **nothing** except an LLM key, and `dev-local.sh` brings up this stack:

| Part | Default | What it means |
| --- | --- | --- |
| **LLM (answers)** | **Groq**, model `openai/gpt-oss-20b` (`https://api.groq.com/openai`) | generation runs on Groq's free tier — this is the one thing you need a key for |
| **Embeddings** | **local** `all-MiniLM-L6-v2` ONNX, **384-dim**, in-process | documents are vectorized on your machine; nothing leaves it for embedding |
| **Retrieval** | **`vector`** (dense similarity in pgvector), top-4 chunks | `app.rag.top-k=4` |
| **Vector store** | PostgreSQL + pgvector (throwaway container) | wiped on `down` |
| **Cache** | Redis semantic cache (throwaway), similarity ≥ 0.95 | repeat questions skip the LLM |
| **Guardrails** | on — PII redaction, prompt-injection filtering, output filter | |
| **Rate limit** | 60 requests/min per client | |
| **URL** | `http://localhost:18080` | override with `RAG_PORT` |

The **only** thing you must provide is `OPENAI_API_KEY` (for `/api/chat`). Ingest, health,
and metrics work with no key at all.

## Testing the API (curl / Postman)

Base URL `http://localhost:18080`. All JSON unless noted.

| Method | Path | Body |
| --- | --- | --- |
| POST | `/api/ingest` | multipart form field `files` (+ optional `aclTags`) |
| POST | `/api/documents` | JSON array of chunks `{documentId, content, sectionPath?, metadata?, aclTags?}` |
| POST | `/api/chat` | `{"question":"..."}` (optional header `X-Roles: roleA,roleB`) |
| GET | `/swagger-ui.html` | interactive API explorer |

```bash
curl -F "files=@mydoc.md;type=text/markdown" http://localhost:18080/api/ingest
curl -XPOST http://localhost:18080/api/chat -H 'Content-Type: application/json' \
     -d '{"question":"What does the document say about X?"}'
```
In Postman: import nothing needed — point requests at `http://localhost:18080`, set
`Content-Type: application/json` for `/api/chat` and `/api/documents`, or use the
*form-data* body with key `files` (type File) for `/api/ingest`. The live OpenAPI spec is
at `/v3/api-docs` if you want to import it.

## The API key is yours to provide

The LLM key is **never** stored in this repository. It is resolved at run time, in order:

1. The `OPENAI_API_KEY` environment variable, if set.
2. `~/.config/rag-app/groq.env` — a local file **outside** the repo (convenient for your
   own machine). Create it once:
   ```bash
   mkdir -p ~/.config/rag-app
   printf 'export OPENAI_API_KEY=%s\n' "<your-key>" > ~/.config/rag-app/groq.env
   chmod 600 ~/.config/rag-app/groq.env
   ```

Everyone else just exports it instead:
```bash
export OPENAI_API_KEY=<your-key>
```
Or pass it inline for a single run: `OPENAI_API_KEY=<key> bash scripts/dev-local.sh up`.

In Kubernetes the key comes from a Secret (`app.secrets.llmApiKey.existingSecret`) — see
[Deployment](deployment.md). Without a key the app still runs; only `/api/chat` fails.

## The dev loop

```bash
# 1. First time (or after changing code): build the image and start the stack
bash scripts/dev-local.sh rebuild

# 2. Test the API (ingest a sample doc + ask a question)
bash scripts/dev-local.sh test
#    …or hit it yourself:
curl -F "files=@mydoc.md;type=text/markdown" http://localhost:18080/api/ingest
curl -XPOST http://localhost:18080/api/chat -H 'Content-Type: application/json' \
     -d '{"question":"..."}'

# 3. Stop and wipe everything (uploaded text, database, cache all gone)
bash scripts/dev-local.sh down

# 4. Make more changes, then repeat from step 1.
```

Your incremental cycle is exactly:

> **edit code → `dev-local.sh rebuild` → `dev-local.sh test` → `dev-local.sh down` → repeat**

If you only changed config/data (not code), use `up` instead of `rebuild` to skip the
image build.

### Commands

| Command | Does |
| --- | --- |
| `dev-local.sh up` | Start the stack (builds the image first if missing). Prints the base URL. |
| `dev-local.sh rebuild` | Rebuild the image from source, then (re)start. |
| `dev-local.sh test` | Ingest a sample doc and ask one question (quick smoke of the API). |
| `dev-local.sh logs` | Follow the app logs. |
| `dev-local.sh status` | Show the containers and current health. |
| `dev-local.sh down` | Stop and **remove** containers, network, and all data. |

The host port defaults to `18080`; override with `RAG_PORT=9000 bash scripts/dev-local.sh up`.

## Tuning — use a different LLM, embeddings, or retrieval

Everything is an environment variable (defaults live in `scripts/config.sh`, the same
knobs as Helm). Set them inline before the command, or edit `config.sh` to change your
own defaults. The script prints the active config when it starts, e.g.
`embeddings=local (dim 384) · retrieval=vector · llm=openai/gpt-oss-20b @ …`.

### Use a different LLM (any OpenAI-compatible endpoint)
```bash
# OpenAI instead of Groq
OPENAI_BASE_URL=https://api.openai.com LLM_MODEL=gpt-4o-mini OPENAI_API_KEY=<key> \
  bash scripts/dev-local.sh up

# Local Ollama — no cloud key (run `ollama pull llama3.1` first)
OPENAI_BASE_URL=http://host.docker.internal:11434 LLM_MODEL=llama3.1 OPENAI_API_KEY=ollama \
  bash scripts/dev-local.sh up
```

### Switch retrieval to hybrid (vector + full-text + RBAC + RRF)
```bash
RETRIEVAL_MODE=hybrid bash scripts/dev-local.sh up
```

### Use a hosted embeddings API for the query/cache path
```bash
EMBEDDING_PROVIDER=openai EMBEDDING_DIMENSIONS=1536 \
  EMBEDDING_OPENAI_BASE_URL=https://api.openai.com EMBEDDING_OPENAI_MODEL=text-embedding-3-small \
  EMBEDDING_API_KEY=<key> bash scripts/dev-local.sh rebuild
```

### All tunables
| Env var | Default | Options |
| --- | --- | --- |
| `OPENAI_BASE_URL` | `https://api.groq.com/openai` | any OpenAI-compatible chat endpoint (Groq / OpenAI / Ollama / vLLM) |
| `LLM_MODEL` | `openai/gpt-oss-20b` | a model your endpoint serves |
| `RETRIEVAL_MODE` | `vector` | `vector` (dense only) · `hybrid` (vector + full-text + RBAC + RRF) |
| `EMBEDDING_PROVIDER` | `local` | `local` (bundled ONNX) · `openai` (external embeddings API) |
| `EMBEDDING_DIMENSIONS` | `384` | must match the embedding model (e.g. `1536` for OpenAI `text-embedding-3-small`) |
| `EMBEDDING_OPENAI_BASE_URL` / `EMBEDDING_OPENAI_MODEL` / `EMBEDDING_API_KEY` | OpenAI defaults | used when `EMBEDDING_PROVIDER=openai` |
| `RAG_PORT` | `18080` | host port |
| `MIRROR`, `APP_IMAGE`, `PGVECTOR_IMAGE`, `REDIS_IMAGE` | Docker Hub | registry / image overrides |

> Note: the bundled local embedding model is always baked into the image (its backend
> loads at startup), so the first build downloads it regardless of `EMBEDDING_PROVIDER`.
> Changing `EMBEDDING_DIMENSIONS` means re-ingesting (vectors of different sizes aren't
> comparable).

## Why everything is clean after `down`

The containers run with **no persistent volumes** — Postgres data, Redis cache, and the
app's temp files live only inside the container writable layers. `down` does
`docker rm -f`, which deletes those layers, so each `up` starts from an empty database
and cache. Nothing leaks between iterations and nothing is left on your host.

## Other local test entry points

| Script | Scope | LLM key |
| --- | --- | --- |
| `dev-local.sh` | Interactive dev loop (start / test / stop) | optional |
| `scripts/e2e-compose.sh` | One-shot automated: offline boot, ingest, storage, metrics | not needed |
| `scripts/e2e-full.sh` | One-shot automated: downloads a real doc, asks the **live LLM** | required |
| `mvn -B verify` | Unit + component + integration tests (Testcontainers) + coverage gate | not needed |

## Prerequisites

- Docker (the stack and the image build both run in Docker — no local JDK/Maven needed
  for `dev-local.sh`).
- `scripts/dev-local.sh` auto-downloads what the build bakes in the first time: the
  embedding model via `scripts/fetch-model.sh` (~87 MB) and the DJL native cache via
  `scripts/prefetch-djl-cache.sh`. You can also run those yourself before building.
- Base images come from Docker Hub by default (override with `MIRROR=<registry>`); the
  Maven build resolves from Maven Central, or your `~/.m2/settings.xml` if present (passed
  to the build as a secret automatically).
