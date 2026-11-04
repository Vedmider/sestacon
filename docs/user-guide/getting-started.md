# Getting Started

## Prerequisites

- JDK 21 and Maven (a local Temurin JDK 21 + Maven are wired via `env.sh`).
- Docker (for Testcontainers-based integration tests and building the image).
- For a real `/api/chat` answer: an API key for any OpenAI-compatible provider
  (Groq's free tier works out of the box — base URL is preconfigured).

## Large files (not in git) — get them before building

Two artifacts are too large to commit and are `.gitignore`d. **Easiest path: the scripts
download them to the correct place for you** — and `dev-local.sh` runs them automatically
on first build, so normally you do nothing. The details below are only if you want to
fetch them by hand.

| Artifact | ~Size | Exact location in the repo | Fetch with |
| --- | --- | --- | --- |
| Embedding model `model.onnx` (all-MiniLM-L6-v2) | 87 MB | `src/main/resources/onnx/all-MiniLM-L6-v2/model.onnx` | `bash scripts/fetch-model.sh` |
| DJL native cache (libtorch + tokenizers) | 433 MB | `docker/djl-cache/` | `bash scripts/prefetch-djl-cache.sh` |

(The matching `tokenizer.json` is already committed, so only `model.onnx` is missing.)

The Docker image is **built with both baked in**, so they must exist before `docker build`
(`dev-local.sh` fetches them if missing). The DJL cache must be pre-staged for the build —
it is not downloaded at container start.

### Download the model by hand
Put it at the exact path in the table:
```bash
mkdir -p src/main/resources/onnx/all-MiniLM-L6-v2
curl -fSL -o src/main/resources/onnx/all-MiniLM-L6-v2/model.onnx \
  https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx
```

### Or keep the model anywhere and point the app at it
```bash
MODEL_DIR=/my/models bash scripts/fetch-model.sh         # download to a custom dir, then…
EMBEDDING_MODEL_URI=file:/my/models/model.onnx bash scripts/dev-local.sh up
```
(`EMBEDDING_MODEL_URI` also accepts `classpath:` and `https:`.) See
[Configuration](configuration.md) for all path overrides.

## 1. Build and test

```bash
source env.sh      # sets JDK 21 + Maven on PATH (and PYTORCH_PRECXX11 for this host)
mvn -B verify      # compiles, runs unit + Testcontainers integration tests
```

## 2. Run locally

The app needs PostgreSQL (with the pgvector extension) and, optionally, Redis.
Start them with Docker, then run the app:

```bash
docker run -d --name pg -p 5432:5432 \
  -e POSTGRES_DB=ragdb -e POSTGRES_USER=rag -e POSTGRES_PASSWORD=rag \
  pgvector/pgvector:pg16
docker run -d --name redis -p 6379:6379 redis:7-alpine

source env.sh
OPENAI_API_KEY=<your-key> mvn spring-boot:run
```

The app starts on `http://localhost:8080`. Defaults connect to `localhost` Postgres
(`ragdb`/`rag`/`rag`) and Redis — override with `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT` (see [Configuration](configuration.md)).

Without `OPENAI_API_KEY` the app still runs — ingest, health and metrics work; only
`/api/chat` fails (it needs a real key to call the LLM).

## 3. First ingest + chat

```bash
# Upload a document
echo "The Eiffel Tower is 330 metres tall and was completed in 1889 in Paris." > fact.txt
curl -F files=@fact.txt http://localhost:8080/api/ingest
# -> {"totalChunks":1,"results":[{"file":"fact.txt","chunks":1}]}

# Ask a question grounded in it
curl -XPOST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"How tall is the Eiffel Tower?"}'
# -> {"answer":"...330 metres...","sources":["fact.txt"],"cached":false}
```

Ask the same question again and you'll get `"cached":true` (served from the Redis
semantic cache, no LLM call).

Browse the API interactively at **`http://localhost:8080/swagger-ui.html`** (OpenAPI
spec at `/v3/api-docs`).

## 4. Build the container image

The image bakes the ONNX embedding model (inside the jar) and the DJL native
libraries so embeddings run **offline** — no downloads at container start.

```bash
bash scripts/prefetch-djl-cache.sh    # stages docker/djl-cache (~434MB) once
DOCKER_BUILDKIT=1 docker build -t rag-app:0.1.0 .
```

The build uses Docker Hub and Maven Central by default. Nothing is tied to a specific
registry or source — override any of it:

```bash
DOCKER_BUILDKIT=1 docker build \
  --build-arg REGISTRY=my-mirror.example.com/library \
  -t rag-app:0.1.0 .
# or override whole image refs:
#   --build-arg BUILD_IMAGE=my/maven:3.9-jdk21 --build-arg RUNTIME_IMAGE=my/jre:21
# and, behind a private Maven mirror, pass your settings.xml as a secret (optional):
#   --secret id=m2settings,src=$HOME/.m2/settings.xml
```

The helper scripts read the same overrides from `scripts/config.sh` (e.g. `MIRROR`,
`APP_IMAGE`, `PGVECTOR_IMAGE`, `REDIS_IMAGE`, `BUILD_IMAGE`, `RUNTIME_IMAGE`). When
provided, the `settings.xml` is passed as a secret so registry credentials never land in
an image layer.

## 5. Verify end to end

```bash
bash scripts/smoke-docker.sh          # container boots, /actuator/health = UP
bash scripts/e2e-compose.sh           # image + real pgvector + redis: ingest, store, metrics (no key needed)
bash scripts/e2e-full.sh              # downloads a real doc, ingests it, asks the LIVE LLM questions
```

`e2e-full.sh` reads the LLM key from `~/.config/rag-app/groq.env` (or `$OPENAI_API_KEY`),
downloads a real document, uploads it, and verifies the answers cite the uploaded source.

The Maven build itself runs 104 tests across unit, component (`@WebMvcTest`), and
integration (Testcontainers) layers, with a coverage gate — see
[Technical ▸ Internals ▸ Tests](../technical/internals.md#tests).

Next: deploy to Kubernetes — see [Deployment](deployment.md).
