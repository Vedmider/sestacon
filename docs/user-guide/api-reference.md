# API Reference

Base URL: `http://<host>:8080` (or behind the Ingress host in Kubernetes).
All request/response bodies are JSON unless noted. Rate limiting applies to
`/api/ingest` and `/api/chat` (see [Operations](operations.md#rate-limiting)).

## Interactive API docs (OpenAPI / Swagger)

The service publishes an OpenAPI 3 spec (via springdoc) and a Swagger UI:

| URL | What |
| --- | --- |
| `/swagger-ui.html` | Swagger UI — browse and try the endpoints interactively |
| `/v3/api-docs` | OpenAPI 3 spec (JSON) |
| `/v3/api-docs.yaml` | OpenAPI 3 spec (YAML) |

The spec is generated from the controllers, so it always matches the running code. You
can import `/v3/api-docs` into Postman/Insomnia or use it to generate clients.

---

## POST /api/ingest

Upload one or more documents. Each is parsed (Apache Tika), split into chunks,
embedded locally, and stored in pgvector with a `source` = filename metadata tag.

- Content type: `multipart/form-data`
- Form field: `files` (repeatable — one part per document)
- Optional form field: `aclTags` (repeatable) — roles allowed to retrieve this content under RBAC; omitted → `public`
- Allowed content types (default): `application/pdf`, `text/plain`, `text/markdown`,
  `text/x-markdown`
- Max size per file (default): 10 MB (`app.ingest.max-file-size-bytes`)

### Request

```bash
curl -F files=@handbook.pdf -F files=@notes.md \
  http://localhost:8080/api/ingest
```

### Response `200 OK`

```json
{
  "totalChunks": 42,
  "results": [
    { "file": "handbook.pdf", "chunks": 39 },
    { "file": "notes.md", "chunks": 3 }
  ]
}
```

### Errors

| Status | When |
| --- | --- |
| `400 Bad Request` | No `files` part, an empty file, or the file could not be read |
| `413 Payload Too Large` | A file exceeds `app.ingest.max-file-size-bytes` |
| `415 Unsupported Media Type` | A file's content type is not in the allowed list |
| `429 Too Many Requests` | Rate limit exceeded (includes a `Retry-After` header) |

---

## POST /api/documents

Ingest chunks that already conform to the standard contract — the source-agnostic entry
point. Any producer (a Jira exporter, a log shipper, an ERP extract, a separate parser
service) can push data here without going through file parsing. Each chunk is embedded
and stored just like a parsed one.

- Content type: `application/json`
- Body: a JSON array of chunk objects

| Field | Type | Required | Default |
| --- | --- | --- | --- |
| `documentId` | string | yes | — (used as the citation `source`) |
| `content` | string | yes | — |
| `sectionPath` | string | no | `""` |
| `metadata` | object | no | `{}` |
| `aclTags` | string[] | no | `["public"]` (roles allowed to read it) |

### Request

```bash
curl -XPOST http://localhost:8080/api/documents \
  -H 'Content-Type: application/json' \
  -d '[
        {"documentId":"JIRA-123","sectionPath":"description",
         "content":"Gateway returns ERR-503-GATEWAY under load",
         "metadata":{"project":"RAN"},"aclTags":["engineering"]},
        {"documentId":"policy.md","content":"All staff may view the leave policy."}
      ]'
```

### Response `200 OK`

```json
{ "stored": 2 }
```

### Errors

| Status | When |
| --- | --- |
| `400 Bad Request` | Empty array, or any chunk has a blank `documentId` or `content` |

> Note: `/api/documents` is **not** rate-limited and has no request-size cap — throttle
> and bound it at your gateway. See [Limits & Dimensioning](limits.md).

---

## POST /api/chat

Ask a question. The service redacts PII from the question, checks the semantic
cache, retrieves the most similar chunks, builds a grounded prompt, calls the LLM,
and returns the answer with its sources.

### Request

```json
{ "question": "How tall is the Eiffel Tower?" }
```

```bash
curl -XPOST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"How tall is the Eiffel Tower?"}'
```

Optional header `X-Roles` — a comma-separated list of the caller's roles:

```bash
curl -XPOST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -H 'X-Roles: engineering,support' \
  -d '{"question":"What causes ERR-503-GATEWAY?"}'
```

When the hybrid retrieval store is enabled (`app.retrieval.mode=hybrid`), the answer is
built only from chunks visible to those roles: `public` chunks plus any whose `aclTags`
intersect the header. With the default vector store the header is ignored.

### Response `200 OK`

```json
{
  "answer": "The Eiffel Tower is 330 metres tall.",
  "sources": ["fact.txt"],
  "cached": false
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `answer` | string | The model's grounded answer (output-filtered by guardrails) |
| `sources` | string[] | Distinct `source` filenames of the retrieved chunks |
| `cached` | boolean | `true` if served from the Redis semantic cache (no LLM call) |

### Errors

| Status | When |
| --- | --- |
| `400 Bad Request` | Blank/missing `question`, question over `app.rag.max-question-length` (4000), or the question was blocked as prompt injection |
| `429 Too Many Requests` | Rate limit exceeded (includes a `Retry-After` header) |
| `500 Internal Server Error` | LLM call failed (e.g. missing/invalid API key, provider error) |

Notes:
- PII (email, phone, card, SSN) is redacted from the question before it is embedded,
  cached, or sent to the LLM.
- Questions matching prompt-injection patterns (e.g. "ignore previous instructions")
  are rejected with `400`.

---

## Actuator endpoints

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | Overall health (`UP`/`DOWN`); drives K8s probes |
| `GET /actuator/health/liveness` | Liveness probe |
| `GET /actuator/health/readiness` | Readiness probe (reflects DB availability) |
| `GET /actuator/info` | Build/app info |
| `GET /actuator/prometheus` | Prometheus scrape endpoint (app + custom RAG metrics) |

See [Operations](operations.md) for the custom metric names.
