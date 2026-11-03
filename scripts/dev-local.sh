#!/bin/bash
# Local dev loop for the RAG app. Runs the full stack (app + pgvector + redis) as
# throwaway Docker containers so you can test the API during incremental development,
# then tear everything down — all data (uploaded text, DB, cache) is ephemeral and is
# removed on `down`.
#
# The LLM API key is NEVER stored in this repo. It is resolved at run time from:
#   1. $OPENAI_API_KEY (export it, or pass inline), or
#   2. ~/.config/rag-app/groq.env  (your local file, outside the repo).
# Chat needs a key; ingest/health/metrics work without one.
#
# Usage:
#   scripts/dev-local.sh all       # ONE-SHOT: build → start → status → test → down
#   scripts/dev-local.sh up        # start the stack (build image first if missing)
#   scripts/dev-local.sh rebuild   # rebuild the image, then (re)start
#   scripts/dev-local.sh test      # smoke the API: ingest a doc + ask a question
#   scripts/dev-local.sh logs      # follow app logs
#   scripts/dev-local.sh status    # show containers + health
#   scripts/dev-local.sh down      # stop and REMOVE everything (data wiped)
set -uo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"
IMAGE="$APP_IMAGE"
NET="${NET:-rag-dev-net}"
PG="${PG:-rag-dev-pg}"
RD="${RD:-rag-dev-redis}"
APP="${APP:-rag-dev-app}"
PORT="${RAG_PORT:-18080}"
BASE="http://localhost:${PORT}"

resolve_key() {
  if [ -z "${OPENAI_API_KEY:-}" ] && [ -f "$HOME/.config/rag-app/groq.env" ]; then
    # shellcheck disable=SC1091
    source "$HOME/.config/rag-app/groq.env"
  fi
  export OPENAI_API_KEY="${OPENAI_API_KEY:-not-configured}"
}

build() {
  echo "== build image $IMAGE =="
  if [ ! -d "$REPO/docker/djl-cache/pytorch" ]; then
    bash "$REPO/scripts/prefetch-djl-cache.sh"
  fi
  local secret=""
  [ -f "$HOME/.m2/settings.xml" ] && secret="--secret id=m2settings,src=$HOME/.m2/settings.xml"
  ( cd "$REPO" && DOCKER_BUILDKIT=1 docker build \
      --build-arg BUILD_IMAGE="$BUILD_IMAGE" --build-arg RUNTIME_IMAGE="$RUNTIME_IMAGE" \
      $secret -t "$IMAGE" . )
}

up() {
  resolve_key
  docker image inspect "$IMAGE" >/dev/null 2>&1 || build
  down_quiet
  echo "== start pgvector + redis + app (ephemeral) =="
  docker network create "$NET" >/dev/null
  docker run -d --name "$PG" --network "$NET" \
    -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASS" \
    "$PGVECTOR_IMAGE" >/dev/null
  docker run -d --name "$RD" --network "$NET" "$REDIS_IMAGE" >/dev/null
  for i in $(seq 1 30); do docker exec "$PG" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 && break; sleep 2; done
  docker run -d --name "$APP" --network "$NET" -p "${PORT}:8080" \
    -e DB_URL=jdbc:postgresql://$PG:5432/"$DB_NAME" -e DB_USERNAME="$DB_USER" -e DB_PASSWORD="$DB_PASS" \
    -e REDIS_HOST=$RD -e REDIS_PORT=6379 -e SPRING_PROFILES_ACTIVE=prod \
    -e RETRIEVAL_MODE="${RETRIEVAL_MODE:-vector}" \
    -e OPENAI_API_KEY="$OPENAI_API_KEY" "$IMAGE" >/dev/null
  echo "== waiting for health =="
  for i in $(seq 1 60); do
    [ "$(curl -sS -m 3 -o /dev/null -w '%{http_code}' $BASE/actuator/health 2>/dev/null)" = "200" ] && { ready=1; break; }
    sleep 3
  done
  if [ "${ready:-0}" != "1" ]; then echo "app did not become healthy:"; docker logs "$APP" 2>&1 | tail -30; exit 1; fi
  echo "UP → $BASE   (health: $(curl -sS $BASE/actuator/health))"
  [ "$OPENAI_API_KEY" = "not-configured" ] && echo "NOTE: no LLM key set — /api/chat will fail. Export OPENAI_API_KEY or create ~/.config/rag-app/groq.env."
  echo "Try:  bash scripts/dev-local.sh test     Stop: bash scripts/dev-local.sh down"
}

test_api() {
  echo "== ingest a sample document (file upload → /api/ingest) =="
  local doc=/tmp/rag-dev-sample.md
  printf '# Notes\n\nThe Eiffel Tower is 330 metres tall and was completed in 1889 in Paris.\nIt was designed by Gustave Eiffel.\n' > "$doc"
  curl -sS -m 30 -F "files=@$doc;type=text/markdown" $BASE/api/ingest; echo
  rm -f "$doc"

  echo "== ingest pre-parsed chunks (standard contract → /api/documents) =="
  curl -sS -m 30 -XPOST $BASE/api/documents -H 'Content-Type: application/json' -d '[
      {"documentId":"ops-runbook","sectionPath":"gateway",
       "content":"Gateway failures surface as error code ERR-503-GATEWAY under load.",
       "aclTags":["engineering"]},
      {"documentId":"public-faq","content":"The office reopens in Paris on Monday."}
    ]'; echo

  echo "== ask a question =="
  curl -sS -m 90 -XPOST $BASE/api/chat -H 'Content-Type: application/json' \
    -d '{"question":"How tall is the Eiffel Tower and who designed it?"}'; echo

  echo "== ask with caller roles (X-Roles header; enforced only in hybrid mode) =="
  curl -sS -m 90 -XPOST $BASE/api/chat -H 'Content-Type: application/json' \
    -H 'X-Roles: engineering' \
    -d '{"question":"What is error ERR-503-GATEWAY?"}'; echo
}

down_quiet() { docker rm -f "$APP" "$PG" "$RD" >/dev/null 2>&1; docker network rm "$NET" >/dev/null 2>&1; true; }

status_fn() {
  docker ps --filter "name=rag-dev-" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
  curl -sS $BASE/actuator/health 2>/dev/null && echo
}

run_all() {
  # One-shot: build → start → status → test → teardown. Always cleans up, even on failure.
  trap 'echo "=== DOWN ==="; down' EXIT
  echo "=== BUILD ===";  build
  echo "=== UP ===";     up
  echo "=== STATUS ==="; status_fn
  echo "=== TEST ===";   test_api
  echo "=== all steps completed; tearing down ==="
}

down() {
  echo "== tearing down + wiping all data =="
  down_quiet
  echo "DOWN — containers, network, and all data (uploaded text, DB, cache) removed."
}

case "${1:-}" in
  up)      up ;;
  rebuild) build && up ;;
  test)    test_api ;;
  all)     run_all ;;
  logs)    docker logs -f "$APP" ;;
  status)  status_fn ;;
  down)    down ;;
  *)       echo "usage: $0 {all|up|rebuild|test|logs|status|down}"; exit 2 ;;
esac
