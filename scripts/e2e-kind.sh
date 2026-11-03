#!/bin/bash
# End-to-end test: kind cluster + helm install + ingest + health/metrics checks.
set -uo pipefail
export PATH="$HOME/bin:$PATH"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/config.sh"
CLUSTER="${KIND_CLUSTER:-rag-e2e}"
NS="${K8S_NAMESPACE:-rag}"
REL="${HELM_RELEASE:-rag}"
PG_IMG="$PGVECTOR_IMAGE"
REDIS_IMG="$REDIS_IMAGE"
APP_IMG="$APP_IMAGE"

fail() { echo "E2E FAILED: $1"; kubectl -n $NS get pods 2>/dev/null; exit 1; }

echo "== create kind cluster =="
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || kind create cluster --name "$CLUSTER" --wait 120s || fail "kind create"

echo "== load images into kind (host already has them; avoids registry pulls) =="
kind load docker-image "$APP_IMG" --name "$CLUSTER" || fail "load app"
kind load docker-image "$PG_IMG" --name "$CLUSTER" || fail "load pg"
kind load docker-image "$REDIS_IMG" --name "$CLUSTER" || fail "load redis"

echo "== helm install =="
kubectl create namespace $NS --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install $REL "$REPO/charts/rag-app" -n $NS \
  --set ingress.enabled=false \
  --set global.registry.url="$MIRROR" \
  --set app.image.registry="docker.io" --set app.image.repository="library" \
  --set app.image.name="$APP_IMAGE_NAME" --set app.image.tag="$APP_IMAGE_TAG" --set app.image.pullPolicy=IfNotPresent \
  --set postgres.image.registry="$MIRROR" --set postgres.image.repository=pgvector --set postgres.image.name=pgvector --set postgres.image.tag=pg16 --set postgres.image.pullPolicy=IfNotPresent \
  --set redis.image.registry="$MIRROR" --set redis.image.repository=library --set redis.image.name=redis --set redis.image.tag=7-alpine --set redis.image.pullPolicy=IfNotPresent \
  --set postgres.persistence.size=1Gi \
  --set app.probes.readiness.initialDelaySeconds=30 \
  || fail "helm install"

echo "== wait for postgres + redis =="
kubectl -n $NS rollout status statefulset/$REL-rag-app-postgres --timeout=180s || fail "postgres not ready"
kubectl -n $NS rollout status deploy/$REL-rag-app-redis --timeout=120s || fail "redis not ready"

echo "== wait for app (embeddings load from baked cache; give it time) =="
kubectl -n $NS rollout status deploy/$REL-rag-app --timeout=240s || fail "app not ready"

echo "== port-forward =="
kubectl -n $NS port-forward svc/$REL-rag-app 18080:8080 >/tmp/pf.log 2>&1 &
PF=$!
sleep 6

echo "== health =="
curl -sS -m 10 localhost:18080/actuator/health && echo
echo "== ingest a document =="
printf 'The Eiffel Tower is 330 metres tall and was completed in 1889 in Paris.' > /tmp/fact.txt
curl -sS -m 30 -F files=@/tmp/fact.txt localhost:18080/api/ingest && echo
echo "== metrics present =="
curl -sS -m 10 localhost:18080/actuator/prometheus | grep -E "rag_query_latency|rag_cache|rag_llm_tokens" | head
echo "== verify chunks stored in pgvector =="
kubectl -n $NS exec statefulset/$REL-rag-app-postgres -- psql -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT count(*) FROM vector_store;" 2>/dev/null

kill $PF 2>/dev/null
echo "E2E CORE CHECKS DONE"
