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
APP_IMAGE_NAME="${APP_IMAGE_NAME:-rag-app}"
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
