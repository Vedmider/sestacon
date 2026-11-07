#!/bin/bash
# Shared, fully-overridable configuration for the helper scripts.
#
# Nothing here is tied to a specific registry, image, or credentials. Every value can be
# overridden by exporting the matching environment variable before running any script,
# e.g.  MIRROR=my-registry.example.com PGVECTOR_IMAGE=my/pgvector:pg16 bash scripts/dev-local.sh all

# --- image registry / sources ---------------------------------------------------------
# Base registry used to compose the default image refs below. Point it at Docker Hub,
# a private mirror, or any OCI registry. Overriding a *_IMAGE below ignores this entirely.
MIRROR="${MIRROR:-docker.io}"

# The built application image (name:tag). Also exposed split for Helm/kind.
APP_IMAGE_NAME="${APP_IMAGE_NAME:-sestacon}"
APP_IMAGE_TAG="${APP_IMAGE_TAG:-0.1.0}"
APP_IMAGE="${APP_IMAGE:-${APP_IMAGE_NAME}:${APP_IMAGE_TAG}}"

# Dependency service images.
PGVECTOR_IMAGE="${PGVECTOR_IMAGE:-${MIRROR}/pgvector/pgvector:pg16}"
REDIS_IMAGE="${REDIS_IMAGE:-${MIRROR}/library/redis:7-alpine}"

# Dockerfile base images (passed as build-args; override to retarget registry or versions).
BUILD_IMAGE="${BUILD_IMAGE:-${MIRROR}/library/maven:3.9-eclipse-temurin-21}"
RUNTIME_IMAGE="${RUNTIME_IMAGE:-${MIRROR}/library/eclipse-temurin:21-jre}"

# --- datastore ------------------------------------------------------------------------
DB_NAME="${DB_NAME:-ragdb}"
DB_USER="${DB_USERNAME:-rag}"
DB_PASS="${DB_PASSWORD:-rag}"


# --- AI configuration (mirrors Helm values; override via env or edit the defaults) -----
# Embeddings: local = bundled ONNX model (downloaded by fetch-model.sh, baked at build);
#             openai = external OpenAI-compatible embeddings API (no model download needed).
EMBEDDING_PROVIDER="${EMBEDDING_PROVIDER:-local}"          # local | openai
EMBEDDING_DIMENSIONS="${EMBEDDING_DIMENSIONS:-384}"        # must match the embedding model
EMBEDDING_OPENAI_BASE_URL="${EMBEDDING_OPENAI_BASE_URL:-https://api.openai.com}"
EMBEDDING_OPENAI_MODEL="${EMBEDDING_OPENAI_MODEL:-text-embedding-3-small}"
EMBEDDING_API_KEY="${EMBEDDING_API_KEY:-}"                 # only for EMBEDDING_PROVIDER=openai

# LLM (chat) — any OpenAI-compatible endpoint (Groq default; OpenAI; local Ollama).
OPENAI_BASE_URL="${OPENAI_BASE_URL:-https://api.groq.com/openai}"
LLM_MODEL="${LLM_MODEL:-openai/gpt-oss-20b}"

# Retrieval: vector = dense only; hybrid = vector + full-text + RBAC + RRF.
RETRIEVAL_MODE="${RETRIEVAL_MODE:-vector}"                 # vector | hybrid
