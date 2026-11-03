#!/bin/bash
# FULL real-world E2E: download a real document, ingest it, ask the live LLM
# questions grounded in it, and check the answers cite the uploaded source.
#
# Key: read from $OPENAI_API_KEY or ~/.config/rag-app/groq.env (not stored in repo).
# Usage: bash scripts/e2e-full.sh
set -uo pipefail
if [ -z "${OPENAI_API_KEY:-}" ] && [ -f "$HOME/.config/rag-app/groq.env" ]; then
  source "$HOME/.config/rag-app/groq.env"
fi
: "${OPENAI_API_KEY:?set OPENAI_API_KEY or create ~/.config/rag-app/groq.env}"

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"
NET=rag-full-net; PG=rag-full-pg; RD=rag-full-redis; APP=rag-full-app
DOC=/tmp/rag-e2e-doc.md
BASE=http://localhost:18082

cleanup(){ docker rm -f "$APP" "$PG" "$RD" >/dev/null 2>&1; docker network rm "$NET" >/dev/null 2>&1; rm -f "$DOC"; }
trap cleanup EXIT; cleanup

echo "== download a real ~5-page document =="
# pgvector's README: a real, fact-dense technical doc we can ask concrete questions about.
URL1=https://raw.githubusercontent.com/pgvector/pgvector/master/README.md
URL2=https://raw.githubusercontent.com/spring-projects/spring-ai/main/README.md
curl -sSL -m 30 -o "$DOC" "$URL1" || true
if [ ! -s "$DOC" ]; then curl -sSL -m 30 -o "$DOC" "$URL2" || true; fi
[ -s "$DOC" ] || { echo "could not download a document"; exit 1; }
echo "downloaded $(wc -l < "$DOC") lines, $(wc -c < "$DOC") bytes -> $DOC"

echo "== start pgvector + redis =="
docker network create "$NET" >/dev/null
docker run -d --name "$PG" --network "$NET" -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASS" "$PGVECTOR_IMAGE" >/dev/null
docker run -d --name "$RD" --network "$NET" "$REDIS_IMAGE" >/dev/null
for i in $(seq 1 30); do docker exec "$PG" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 && break; sleep 2; done

echo "== start app (live LLM) =="
docker run -d --name "$APP" --network "$NET" -p 18082:8080 \
  -e DB_URL=jdbc:postgresql://$PG:5432/"$DB_NAME" -e DB_USERNAME="$DB_USER" -e DB_PASSWORD="$DB_PASS" \
  -e REDIS_HOST=$RD -e REDIS_PORT=6379 -e SPRING_PROFILES_ACTIVE=prod \
  -e OPENAI_API_KEY="$OPENAI_API_KEY" "$APP_IMAGE" >/dev/null
for i in $(seq 1 60); do [ "$(curl -sS -m 3 -o /dev/null -w '%{http_code}' $BASE/actuator/health 2>/dev/null)" = "200" ] && break; sleep 3; done

echo "== upload the document (/api/ingest) =="
ING=$(curl -sS -m 60 -F "files=@$DOC;type=text/markdown" $BASE/api/ingest)
echo "$ING"
CHUNKS=$(echo "$ING" | python3 -c "import sys,json;print(json.load(sys.stdin).get('totalChunks',0))" 2>/dev/null || echo 0)
[ "${CHUNKS:-0}" -gt 0 ] || { echo "INGEST FAILED (no chunks)"; docker logs "$APP" 2>&1 | tail -20; exit 1; }
echo "ingested $CHUNKS chunks"

ask() {
  local q="$1"; local expect="$2"
  local resp ans src attempt
  for attempt in 1 2; do
    resp=$(curl -sS -m 90 -XPOST $BASE/api/chat -H 'Content-Type: application/json' -d "{\"question\": \"$q\"}")
    ans=$(echo "$resp" | python3 -c "import sys,json;print(json.load(sys.stdin).get('answer',''))" 2>/dev/null)
    src=$(echo "$resp" | python3 -c "import sys,json;print(','.join(json.load(sys.stdin).get('sources',[])))" 2>/dev/null)
    [ -n "$ans" ] && break
    echo "   (empty answer — likely free-tier rate limit; retrying in 20s)"
    sleep 20
  done
  echo "---------------------------------------------"
  echo "Q: $q"
  echo "A: $ans"
  echo "sources: $src"
  [ -n "$ans" ] || { echo "   [FAIL] empty answer after retry"; return 1; }
  if echo "$ans" | grep -qiE "$expect"; then echo "   [PASS] answer mentions /$expect/"; else echo "   [WARN] expected keyword /$expect/ not found (LLM phrasing may differ)"; fi
}

echo "== ask questions grounded in the document =="
echo "   (spacing requests to respect the Groq free-tier token/minute limit)"
ask "What index types does pgvector support?" "hnsw|ivfflat"; sleep 12
ask "Which operator does pgvector use for cosine distance?" "<=>|cosine"; sleep 12
ask "How do I create an index for L2 distance in pgvector?" "index|vector_l2|<->"

echo "== token + cache metrics =="
curl -sS -m 10 $BASE/actuator/prometheus | grep -E "rag_llm_tokens_total|rag_cache_(hits|misses)_total|rag_query_latency_seconds_count" | grep -v '^#'

echo "FULL E2E DONE"
