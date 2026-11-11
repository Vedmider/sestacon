# Skill: Sestacon — document & knowledge retrieval

Provider-agnostic guidance for an AI agent on using **Sestacon**. Reformat the frontmatter/wrapper
for your own assistant; the body is the skill.

## What this app is

Sestacon answers **natural-language questions grounded in prose documents** (product docs, runbooks,
design notes, skills/agent definitions, conventions) with **source attribution**. It embeds document
text with a real model and retrieves the most relevant chunks. It is the "what / why / which" layer —
**not** a code index.

Endpoints:
- `POST /api/retrieve` `{query, topK}` (header `X-Roles`) → `[{text, source, score}]` — grounded
  chunks, no LLM (token-cheap; the agent reasons itself).
- `POST /api/chat` → a generated answer grounded in retrieved docs, with sources.
- `POST /api/ingest` (file) / `POST /api/documents` (pre-chunked) → add documents. Re-posting an
  existing `documentId` **replaces** all its chunks (supersession — no duplicates). Both accept an
  optional `sourceSystem` (recorded as provenance). At ingest, chunks are **enriched** with derived
  metadata (rule-based, no LLM): `docType` (from the id's extension), `lang`, `tags` (keyword content
  classification: runbook/api/security/config/architecture), `sourceSystem`, `ingestedAt`.
  Caller-supplied metadata keys are never overwritten.
- `POST /api/retrieve` may include an optional `metadata` map — an exact-match filter (every
  key/value must be present on a chunk), combined with RBAC. E.g. `{"query":"...","metadata":
  {"sourceSystem":"jira","docType":"md"}}`.
- `DELETE /api/documents/{documentId}` → remove a document and all its chunks. All-or-nothing RBAC:
  your `X-Roles` must cover every chunk, else 403 and nothing is deleted; unknown id → `deleted: 0`.

## When to USE it

- A conceptual / explanatory question: "How does X work?", "Why do we do Y?", "What's our
  convention for Z?"
- Finding *which* capability/doc/agent/skill covers something: "Which agent decomposes a Jira epic?"
- Any answer that should be **grounded in your own documents** with citations.
- You want an LLM answer constrained to your corpus (`/api/chat`) — or just the raw grounded chunks
  for your own reasoning (`/api/retrieve`).

## When NOT to use it

- "Where is method/class X in the code?" → use **code-memory** (fuzzy) or **Dylext** (exact).
- You need an exact source span, a symbol's body, callers/tests, or uncommitted edits → **Dylext**.
- Arbitrary text/regex, logs, config, or unindexed files → use **grep/ripgrep**.
- The knowledge isn't in the ingested documents → Sestacon only knows what's been ingested; don't
  expect world knowledge or un-ingested code facts.

## How to use well

1. Prefer `/api/retrieve` when you (the agent) will reason over the chunks yourself — it skips the LLM
   and is far cheaper.
2. Pass `X-Roles` honestly; RBAC hides chunks you aren't entitled to.
3. Retrieval is wide-then-reranked and chunks carry a `[doc > section]` context header — cite the
   `source` field in your answer.
4. If retrieval returns nothing, the KB may be empty/ephemeral (dev) — ingest first, or the topic
   simply isn't documented; say so rather than inventing.

## Fits with the other two apps

concept/docs → **Sestacon** · fuzzy "where is the code" → **code-memory** · exact code + live edits →
**Dylext**. In the unified setup, Dylext's `explain` tool delegates here.


---

## ⚠️ PREREQUISITE — ingest the repo's explanatory content BEFORE working

> **Before reasoning about a repo, its explanatory content MUST be in Sestacon.** This is how the
> AI "realizes why the repo exists, its structure, and which part maps to which functionality" —
> the same orientation a human does before editing. If that content is absent, **ingest it first.**

Two ways to satisfy the prerequisite:

1. **Static (preferred).** The repo ships explanatory docs (purpose, architecture, part→function map)
   under e.g. `docs/` or `docs/EXPLAIN/`. Ingest those once:
   `POST /api/documents` (or `/api/ingest`) with a stable `documentId` per file, e.g.
   `explain:<repo>:<path>`. Treat this as a **repo requirement** — a repo should carry its own
   explanation.
2. **Analyze-once fallback.** If the repo has none, the AI analyzes it once (structure + purpose +
   how parts interact), writes that explanation, and ingests it under `explain:<repo>:...`. That
   ingested result becomes the repo's static explanatory content going forward.

Then retrieval works as a lookup by stable id (findable + replaceable, like a database record):
- `explain:<repo>:*` → why/architecture/part map (orientation before editing).
- Re-ingest (same `documentId`) whenever the repo's structure changes materially.

### Keeping the corpus current (supersession)
A `documentId` is the lifecycle key. Because re-ingesting an id **replaces** its chunks, the right
way to update a document (a law, a spec, a runbook that changed) is to re-post it under the **same
`documentId`** — the old version is overwritten atomically, so retrieval only ever sees the current
one. To retire a document entirely, `DELETE /api/documents/{documentId}`. Do **not** encode the
version in the id (`law:x:v2`) — that strands the old version in the store; keep the id stable and
let replace supersede. There is no version history (by design); if you need an audit trail, keep it
outside Sestacon.

Skip this and the AI will navigate code blind to intent — exactly the orientation step a human never
skips. The code-location tools (code-memory, Dylext) tell you *where*; this tells you *why*.
