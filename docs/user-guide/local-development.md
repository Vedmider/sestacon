# Local Development & End-to-End Testing

This is a Kubernetes-oriented app, but you can run and test the **whole stack locally**
during incremental development. The `scripts/dev-local.sh` helper runs the app plus
PostgreSQL/pgvector and Redis as throwaway Docker containers, lets you exercise the API,
and wipes everything on stop.

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

- Docker (the stack and image build run in Docker).
- For building the image: `scripts/prefetch-djl-cache.sh` is run automatically by
  `rebuild` if the DJL cache is missing. See [Getting Started](getting-started.md).
- Base images are pulled from Docker Hub by default (override with `MIRROR=<registry>`);
  the Maven build resolves dependencies from Maven Central, or through your
  `~/.m2/settings.xml` if present (passed to the build as a secret automatically).
