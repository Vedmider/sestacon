# AGENTS.md — start here (any AI)

This is the single entry point for an AI agent working with **Sestacon**. It is AI-agnostic: Claude,
Cursor, Kiro, Codex, Aider, or anything that can read a repo and make an HTTP call. Read this file,
wire yourself in, and follow the usage policy it points at. Everything below links to a single
source of truth — nothing here is a second copy of the policy.

## What this app is (30 seconds)

Sestacon is the **document & knowledge layer**: upload prose (product docs, runbooks, design notes,
skills, conventions) and ask natural-language questions answered **grounded in those documents**,
with source attribution. It is the "what / why / which" brain — **not** a code index. It embeds text
locally (MiniLM, in-cluster) and retrieves the most relevant chunks; an optional chat path adds an
LLM answer on top.

## How to wire your AI in

### 1. Run the service

```bash
export OPENAI_API_KEY=<your-key>   # only needed for /api/chat; /api/retrieve works without it
bash scripts/dev-local.sh up       # downloads the model if missing, builds, starts on :18080
```

### 2. Call the API

Sestacon is a plain **HTTP/REST** service (not MCP) on `http://localhost:18080`. Any AI that can make
an HTTP request can use it — via your own helper (e.g. a local `apicall.py`), the client's HTTP
tool, or `curl`. The three endpoints:

- `POST /api/retrieve` `{query, topK}` + header `X-Roles` → `[{text, source, score}]` — grounded
  chunks, no LLM (token-cheap; you reason over them yourself). **Prefer this as an agent.**
- `POST /api/chat` `{question}` → a generated answer grounded in retrieved docs, with sources.
- `POST /api/ingest` (file) / `POST /api/documents` (pre-chunked) → add documents. Re-posting an
  existing `documentId` **replaces** its chunks; `DELETE /api/documents/{documentId}` removes it
  (all-or-nothing RBAC via `X-Roles`).

### 3. PREREQUISITE — ingest the repo's explanatory content first

**Before reasoning about any repo, its explanatory content must be in Sestacon** (purpose,
architecture, which part maps to which functionality) — the same orientation a human does before
editing. If it's absent, ingest it first under a stable id like `explain:<repo>:<path>`. Full rule:
[`docs/SKILL.md`](docs/SKILL.md) → "PREREQUISITE".

### 4. Verify the wiring

Ingest one doc, then retrieve — if you get a scored chunk back, you're connected:

```bash
# ingest
curl -F "files=@README.md;type=text/markdown" http://localhost:18080/api/ingest
# retrieve
curl -XPOST http://localhost:18080/api/retrieve -H 'Content-Type: application/json' \
     -H 'X-Roles: public' -d '{"query":"what is this app","topK":3}'
```

## The usage policy (READ THIS — it's the whole point)

The soft policy — when to use Sestacon vs code tools, how to retrieve well, the ingest prerequisite —
is authored once in:

- **[`docs/SKILL.md`](docs/SKILL.md)** — what Sestacon is, when to USE / when NOT to, how to use well
  (prefer `/api/retrieve`, pass `X-Roles` honestly, cite `source`), and the ingest prerequisite.
  **This is the policy. Load it and follow it.**

One-line version: **concept / "what / why / which" grounded in your docs → Sestacon.** For "where is
method X", exact code spans, or uncommitted edits, that's **Dylext** / code-memory — not this.

## Two ways to adopt the policy

You choose how strongly to bind the AI to it:

- **Soft (default, agnostic)** — the policy is delivered as a skill/steering file your client
  auto-loads. Thin per-client pointers already exist (`CLAUDE.md`, `.cursor/rules/usage.md`,
  `.kiro/steering/usage.md`) and all point back here. No setup; the AI *should* follow it. An
  autonomous agent can still ignore it under pressure.
- **Hard (opt-in, Claude Code)** — if you observe the AI answering conceptual/"why" questions from
  raw `grep` instead of asking Sestacon, install a `PreToolUse` speed-bump that makes it **confirm**
  first (it asks, it does not hard-block). See **[`hooks/claude/install.md`](hooks/claude/install.md)**.
  Claude-specific by nature (hook schemas aren't portable); the soft layer covers every other client.
