#!/bin/bash
# Live E2E: full stack + a REAL LLM call. The key is NOT stored in this repo.
# It is read from $OPENAI_API_KEY, or from ~/.config/rag-app/groq.env if present.
# Usage: bash scripts/e2e-live-chat.sh   (or  OPENAI_API_KEY=<key> bash ...)
set -uo pipefail
if [ -z "${OPENAI_API_KEY:-}" ] && [ -f "$HOME/.config/rag-app/groq.env" ]; then
  # shellcheck disable=SC1091
  source "$HOME/.config/rag-app/groq.env"
fi
: "${OPENAI_API_KEY:?set OPENAI_API_KEY or create ~/.config/rag-app/groq.env}"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"
NET=rag-live-net
PG=rag-live-pg
RD=rag-live-redis
APP=rag-live-app

cleanup() {
  docker rm -f "$APP" "$PG" "$RD" >/dev/null 2>&1
  docker network rm "$NET" >/dev/null 2>&1
}
trap cleanup EXIT
cleanup

docker network create "$NET" >/dev/null
echo "== start pgvector + redis =="
docker run -d --name "$PG" --network "$NET" \
  -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASS" \
  "$PGVECTOR_IMAGE" >/dev/null
docker run -d --name "$RD" --network "$NET" "$REDIS_IMAGE" >/dev/null

for i in $(seq 1 30); do docker exec "$PG" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 && break; sleep 2; done

echo "== start app with live LLM key =="
docker run -d --name "$APP" --network "$NET" -p 18080:8080 \
  -e DB_URL=jdbc:postgresql://$PG:5432/"$DB_NAME" -e DB_USERNAME="$DB_USER" -e DB_PASSWORD="$DB_PASS" \
  -e REDIS_HOST=$RD -e REDIS_PORT=6379 -e SPRING_PROFILES_ACTIVE=prod \
  -e OPENAI_API_KEY="$OPENAI_API_KEY" \
  "$APP_IMAGE" >/dev/null

echo "== wait for health =="
for i in $(seq 1 60); do
  [ "$(curl -sS -m 3 -o /dev/null -w '%{http_code}' localhost:18080/actuator/health 2>/dev/null)" = "200" ] && break
  sleep 3
done

echo "== ingest =="
printf 'The Eiffel Tower is 330 metres tall and was completed in 1889 in Paris. It was designed by Gustave Eiffel.' > /tmp/fact.txt
curl -sS -m 30 -F files=@/tmp/fact.txt localhost:18080/api/ingest; echo

echo "== chat (1st call -> live LLM) =="
curl -sS -m 60 -XPOST localhost:18080/api/chat -H 'Content-Type: application/json' \
  -d '{"question":"How tall is the Eiffel Tower and who designed it?"}'; echo

echo "== chat (2nd identical -> semantic cache) =="
curl -sS -m 60 -XPOST localhost:18080/api/chat -H 'Content-Type: application/json' \
  -d '{"question":"How tall is the Eiffel Tower and who designed it?"}'; echo

echo "== token metrics =="
curl -sS -m 10 localhost:18080/actuator/prometheus | grep -E "rag_llm_tokens_total|rag_cache_(hits|misses)_total" | grep -v "^#"

rm -f /tmp/fact.txt
echo "LIVE E2E DONE"
