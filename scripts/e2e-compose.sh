#!/bin/bash
# Integrated E2E without k8s: run the real app image against real pgvector + redis
# containers (same images/env the Helm chart injects). Proves the production image
# boots offline (baked DJL cache), connects to DB+cache, ingests, and serves metrics.
set -uo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"
NET=rag-e2e-net
PG=rag-e2e-pg
RD=rag-e2e-redis
APP=rag-e2e-app

cleanup() {
  docker rm -f "$APP" "$PG" "$RD" >/dev/null 2>&1
  docker network rm "$NET" >/dev/null 2>&1
}
trap cleanup EXIT
cleanup

docker network create "$NET" >/dev/null

echo "== start pgvector =="
docker run -d --name "$PG" --network "$NET" \
  -e POSTGRES_DB="$DB_NAME" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASS" \
  "$PGVECTOR_IMAGE" >/dev/null

echo "== start redis =="
docker run -d --name "$RD" --network "$NET" \
  "$REDIS_IMAGE" >/dev/null

echo "== wait for postgres =="
for i in $(seq 1 30); do
  docker exec "$PG" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 && break
  sleep 2
done

echo "== start app (offline embeddings from baked DJL cache) =="
docker run -d --name "$APP" --network "$NET" -p 18080:8080 \
  -e DB_URL=jdbc:postgresql://$PG:5432/"$DB_NAME" \
  -e DB_USERNAME="$DB_USER" -e DB_PASSWORD="$DB_PASS" \
  -e REDIS_HOST=$RD -e REDIS_PORT=6379 \
  -e SPRING_PROFILES_ACTIVE=prod \
  "$APP_IMAGE" >/dev/null

echo "== wait for app health =="
ok=0
for i in $(seq 1 60); do
  code=$(curl -sS -m 3 -o /tmp/h.json -w "%{http_code}" localhost:18080/actuator/health 2>/dev/null)
  if [ "$code" = "200" ]; then ok=1; echo "health: $(cat /tmp/h.json)"; break; fi
  sleep 3
done
if [ "$ok" != "1" ]; then echo "APP DID NOT BECOME HEALTHY; last logs:"; docker logs "$APP" 2>&1 | tail -40; exit 1; fi

echo "== ingest a document =="
printf 'The Eiffel Tower is 330 metres tall and was completed in 1889 in Paris.' > /tmp/fact.txt
curl -sS -m 30 -F files=@/tmp/fact.txt localhost:18080/api/ingest; echo

echo "== chunks stored in pgvector =="
docker exec "$PG" psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT count(*) FROM vector_store;"

echo "== metrics present =="
curl -sS -m 10 localhost:18080/actuator/prometheus | grep -E "rag_cache_misses|rag_query_latency|rag_llm_tokens" | head

echo "== confirm DJL loaded offline (no publish.djl.ai download) =="
docker logs "$APP" 2>&1 | grep -ciE "Downloading https://publish.djl.ai" | sed 's/^/publish.djl.ai downloads: /'

echo "E2E-COMPOSE DONE"
