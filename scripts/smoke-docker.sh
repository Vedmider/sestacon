#!/bin/bash
# Bounded container smoke test: start detached, poll /actuator/health, tear down.
set -u
IMAGE="${1:-rag-app:0.1.0}"
NAME=rag-smoke
PORT=18080

docker rm -f "$NAME" >/dev/null 2>&1 || true
cid=$(docker run -d --name "$NAME" -p ${PORT}:8080 "$IMAGE")
echo "container: ${cid:0:12}"

rc=1
for i in $(seq 1 30); do
  code=$(curl -sS -m 3 -o /tmp/rag_health.json -w "%{http_code}" "http://localhost:${PORT}/actuator/health" 2>/dev/null)
  if [ "$code" = "200" ]; then
    echo "HTTP $code -> $(cat /tmp/rag_health.json)"
    rc=0
    break
  fi
  sleep 2
done

echo "--- runtime user ---"
docker exec "$NAME" id 2>/dev/null || true
echo "--- image size ---"
docker image inspect "$IMAGE" --format '{{.Size}}' | awk '{printf "%.0f MB\n",$1/1024/1024}'

if [ $rc -ne 0 ]; then
  echo "SMOKE FAILED; last logs:"
  docker logs "$NAME" 2>&1 | tail -20
fi

docker rm -f "$NAME" >/dev/null 2>&1 || true
exit $rc
