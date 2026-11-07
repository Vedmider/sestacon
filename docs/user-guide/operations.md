# Operations

## Metrics

Scrape `GET /actuator/prometheus`. Alongside standard JVM/HTTP metrics, the service
exposes custom RAG metrics:

| Metric | Type | Meaning |
| --- | --- | --- |
| `rag_query_latency_seconds` | timer | End-to-end `/api/chat` latency |
| `rag_cache_hits_total` | counter | Semantic cache hits |
| `rag_cache_misses_total` | counter | Semantic cache misses |
| `rag_guardrail_blocks_total` | counter | Requests blocked by guardrails |
| `rag_llm_tokens_total{type="prompt"}` | counter | Prompt tokens used |
| `rag_llm_tokens_total{type="completion"}` | counter | Completion tokens used |

Useful derived signals:
- Cache hit ratio: `rate(rag_cache_hits_total[5m]) / (rate(rag_cache_hits_total[5m]) + rate(rag_cache_misses_total[5m]))`
- Token spend: `rate(rag_llm_tokens_total[1h])`
- p95 latency: `histogram_quantile(0.95, rag_query_latency_seconds)`

In Kubernetes, enable scraping via `metrics.serviceMonitor.enabled=true` (Prometheus
Operator) or rely on the pod annotations added when `metrics.enabled=true`.

## Logging & security audit

The app uses SLF4J/Logback via `logback-spring.xml`:

- **dev** profile — human-readable console lines with request correlation and markers.
- **prod** profile (set by the Helm chart) — **structured JSON** (`LogstashEncoder`) for
  K8s log pipelines (Loki/ELK). Every line carries MDC fields `requestId`, `clientIp`,
  `method`, `path`, and `app`.

**Request correlation.** `RequestCorrelationFilter` assigns each request an id (honouring
an inbound `X-Request-Id`, echoed in the response), puts it in the MDC, and emits one
access log per request: `event=http_request status=<code> durationMs=<n>`. The id ties
every log line (including security events) to its request.

**Security audit.** Events are logged under the dedicated `SECURITY` logger and tagged
with the `SECURITY` marker (JSON `"tags":["SECURITY"]`), so they can be filtered, routed,
and alerted on independently:

| Event | Level | When |
| --- | --- | --- |
| `prompt_injection_blocked` | WARN | A question matched an injection pattern (→ 400) |
| `pii_redacted` | INFO | PII found and redacted (logs **types + counts only**) |
| `output_withheld` | WARN | The answer was blocked by the output filter |
| `rate_limited` | WARN | A client exceeded the rate limit (→ 429) |
| `ingest_rejected` | WARN | An upload was rejected (empty / too large / wrong type) |

> **No sensitive data is logged.** Question text, PII values, and the API key never
> appear in logs — only outcomes, counts, client IP, and correlation ids.

**Levels** are set in `application.yml` (`logging.level.root`, `io.github.vedmider.sestacon`,
`SECURITY`) and overridable per environment, e.g.
`--set-string env` or `LOGGING_LEVEL_COM_EXAMPLE_RAG=DEBUG`.

Example (prod JSON):
```json
{"@timestamp":"…","message":"event=prompt_injection_blocked outcome=denied",
 "logger_name":"SECURITY","level":"WARN","path":"/api/chat","method":"POST",
 "requestId":"47f7…","clientIp":"10.0.0.5","tags":["SECURITY"],"app":"Sestacon"}
```

## Health probes

- `/actuator/health/liveness` — process is up.
- `/actuator/health/readiness` — dependencies (the database) are reachable.

The Redis health indicator is intentionally **disabled**: the cache is best-effort,
so a Redis outage must not flip readiness and remove the pod from service.

## Semantic cache

- Entries are `(query embedding → answer)` pairs in Redis with a TTL
  (`app.cache.ttl-seconds`) and FIFO eviction past `app.cache.max-entries`.
- A hit requires cosine similarity ≥ `app.cache.similarity-threshold` (default 0.95).
  Lower it to cache more aggressively (more hits, looser matches); raise it for
  stricter matching.
- If Redis is down, lookups and stores are skipped with a logged warning and the
  request proceeds normally.

Clear the cache by deleting its keys (prefix `ragcache:`) or flushing Redis.

## Guardrails

- **PII redaction** replaces email/phone/card/SSN in the question with placeholders
  before embedding, caching, or sending to the LLM.
- **Prompt-injection detection** rejects known injection patterns with `400`.
- **Output filtering** withholds answers containing any `banned-output-phrases`.

Each block increments `rag_guardrail_blocks_total`. Tune via `app.guardrails.*`
(see [Configuration](configuration.md)). Disable individually (e.g.
`app.guardrails.redact-pii=false`) or entirely (`app.guardrails.enabled=false`).

## Rate limiting

Per-client (by `X-Forwarded-For` first hop, else remote address) token bucket on
`/api/chat` and `/api/ingest`. Exceeding the limit returns `429` with a `Retry-After`
header.

**Caveat:** the limiter is in-memory per pod. With N replicas the effective global
limit is roughly `requests-per-minute × N`. For a precise cluster-wide limit, put the
limit at the ingress/gateway or move the bucket to Redis.

## Troubleshooting

| Symptom | Likely cause / fix |
| --- | --- |
| `/api/chat` returns 500 | Missing/invalid `OPENAI_API_KEY`, or the LLM provider is unreachable. Check the app logs and the Secret. |
| App pod not Ready | Readiness depends on the DB. Check the Postgres pod and `DB_URL`/credentials. |
| Startup slow or fails downloading native libs | DJL native libraries must be present offline. Rebuild with `scripts/prefetch-djl-cache.sh` so they are baked into the image. |
| Ingest returns 415 | File content type not in `app.ingest.allowed-content-types`. |
| Ingest returns 413 | File exceeds `app.ingest.max-file-size-bytes` (or the servlet multipart cap). |
| Answers ignore newly uploaded docs | Confirm ingest succeeded (`totalChunks > 0`) and the question is similar enough to retrieve the chunks (`app.rag.top-k`). |
| Repeated identical questions still call the LLM | Redis unreachable, cache disabled, or similarity below threshold. Check `rag_cache_*` metrics and Redis connectivity. |

## Logs

Structured logs go to stdout (JSON in `prod`) for collection by your platform's log
pipeline. See **Logging & security audit** above for formats, correlation, and the
security event catalogue. In Kubernetes:
`kubectl -n <ns> logs deploy/<release>-Sestacon`. Filter security events on the
`SECURITY` logger / `tags:["SECURITY"]`.
