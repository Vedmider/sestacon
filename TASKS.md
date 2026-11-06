# RAG — Tasks & Roadmap

Standing context: two apps for AI code refactoring with low token usage.
- **`rag-app`** (static/doc) = existing Spring AI + pgvector app, Redis hot tier; returns
  **chunk text**. Holds requirements, skills, conventions, architecture, class-purpose.
  → **Task 1**.
- **`code-memory`** (dynamic/code) = a *different* service (locator index); returns
  **locators** (path+symbol+line range), kiro reads fresh bytes. Optimized for continuous
  code change. → **Task 2**.
- Golden rules: retrieve-only (no LLM inside either app — kiro is the only LLM brain);
  static = hints/cite-backed, dynamic+fresh-read = truth; keep on localhost (auth gap from
  review).

---

## Task 1 — `rag-app`: add `/retrieve` endpoint (retrieve-only, no LLM)

**Goal:** expose the first half of the RAG chain so kiro gets chunks/locators without
spending LLM tokens.

Today: `/chat → embed → search → [cache/guardrails] → ChatProvider(LLM) → answer`.
Add:    `/retrieve → embed → DocumentStore.search → return chunks` (stop before the LLM).

**Scope (additive, ~1 endpoint + 1 small use case):**
- New inbound adapter `POST /api/retrieve` (web).
- New use case: `embed(query)` → `documentStore.search(query, topK, roles)` →
  return `[{text, source, score}]`.
- Reuse as-is: `EmbeddingProvider`, `DocumentStore` (incl. hybrid/RBAC), ingestion, Redis.
- Leave `/chat` + `ChatProvider` untouched (original doc-Q&A path).
- Request: `{ "query": "...", "topK": 4, "roles": ["..."] }`. Response: array of chunks.
- Projection switch: **text** projection (static KB) vs **locator** projection (code) — same
  endpoint shape, different fields returned.

**Done when:** kiro can call `/api/retrieve`, get top-K grounded chunks, and no LLM call is
made on that path. Add a web test asserting no `ChatProvider` invocation.

**STATUS (in progress):** implemented —
- `domain/port/in/RetrieveUseCase.java` (inbound port)
- `application/RetrievalService.java` (@Service, delegates to `DocumentStore.search`; no
  LLM/cache/guardrails)
- `adapter/in/web/RetrieveController.java` (`POST /api/retrieve`, body `{query, topK?}`,
  `X-Roles` header, topK default=`app.rag.top-k`, capped at 50, blank/over-long → 400)
- `adapter/in/web/RetrieveControllerWebTest.java` (asserts chunks returned, topK cap,
  roles parsing, 400s, and `verifyNoInteractions(ragQuery)` = no LLM path)
- Reused unchanged: `DocumentStore` (embeds + hybrid/RBAC internally), `GlobalExceptionHandler`.
- **Verified:** full unit-test suite **green — 86 tests, 0 failures/errors (BUILD SUCCESS)**,
  incl. new `RetrieveControllerWebTest` (5). Ran via the `maven:3.9-eclipse-temurin-21`
  image using the arm.sero mirror + a local writable m2 (host NFS `~/.m2` not Docker-mountable).
- Rate-limited: `/api/retrieve` added to `WebConfig` interceptor (with `/api/chat`, `/api/ingest`).

---

## Task 2 — `code-memory`: git-based auto-sync for the locator index

**App:** `/local/IdeaProjects/code-memory` (Java 21 / Spring / pgvector, hexagonal — sibling
of `rag-app`) + a Go `chunking-service` sidecar. **It already exists and is mostly built.**
Task 2 is now a *targeted addition* (git-driven sync), not a rebuild.

### Already done (do NOT rebuild)
- **Locators, not bodies.** `/api/search` + `/api/context` return `file, symbol, signature,
  lines:[start,end], score` — the symbol body is deliberately omitted (`CodeMemoryController.toDto`).
  kiro reads fresh bytes from `file`+`lines`. ✅
- **AST symbols** — `JavaAstParser` (JDK compiler Tree API, in-process); Go via
  `chunking-service` (`go/parser`); `ParserRouter`+`LanguageDetector`; pluggable `CodeParser`
  port with `mode=java|single|multi`. ✅
- **Dependency graph / blast radius** — `code_edges` (calls/imports); `/context` returns
  `callers` + `tests`. ✅
- **Per-file incremental + delete** — `POST /api/index`, `POST /api/index/file`,
  `DELETE /api/index/file`; `SymbolStore.upsertFile`/`deleteFile`/`resolvePendingEdges`. ✅
- **Per-agent scoping** (`allowed_agents`) + **hybrid retrieval** (vector + full-text + RRF). ✅

### Gaps to close
- **GAP A — no automatic sync.** Indexing only fires on explicit HTTP calls; nothing tracks
  the repo. The AI may not call it.
- **GAP B — no change detection.** No per-file hash, no repo cursor (SHA). `indexPath`
  re-parses *every* file every run (no skip-unchanged).
- **GAP C — line-range freshness.** Locators carry line numbers → **any** edit shifts them →
  reindex the whole touched file. (The "skip body-only change" rule is a `rag-app`/static-tier
  concern, NOT here.)
- **GAP D (optional) — graph is enrichment, not ranking.** No PageRank weighting (Aider/
  Sourcegraph do). Defer.

### Consumer model: bulk AI edits, not live typing
The consumer is an agent doing **bursts of edits**, then reading again — not a human typing.
So: **no filewatch / mtime / live tracking.** Sync on the **edit-transaction / commit
boundary**, and make retrieval **self-healing** so correctness never depends on the agent
remembering to sync.

### Design: git-based auto-sync (chosen)
**Invariant — "commit ⇒ definitely synced."** Any `repo_state.cursor_sha != HEAD` is
mandatory drift and is synced before the next read (never subject to any TTL/throttle).

**Read-gate (self-healing, primary correctness mechanism).** `/api/search` and `/api/context`
run a cheap git gate first, then serve fresh locators:
```
getLocators(repo, root, query):
  1. ensure indexed            # cold start: no repo_state → full indexPath once, set cursor=HEAD
  2. head  = git rev-parse HEAD
     dirty = git status --porcelain              # uncommitted edits/adds/deletes (ms)
     if head == cursor && dirty empty && synced: serve   # steady state → no work
  3. changed = git diff --name-status cursor..head  ∪  dirty   # A/M→reindex, D→remove, R→both
     hash-verify each (code_files.content_hash) to drop no-ops
  4. syncDelta(changed)        # reindex touched files, then resolvePendingEdges once
  5. retrieve(query)           # now fresh
```
Cost: steady state = one `rev-parse` + one `status` (≈ms, effectively free); after edits =
reindex only changed files. **O(changed), not O(repo).**

**Git hooks (immediate, push).** Installer adds `post-commit`, `post-merge`, `post-checkout`,
`post-rewrite` → each `curl POST /api/sync` so a commit/pull/branch-switch/amend updates the
index immediately. (Backstopped by the read-gate if a hook isn't installed.)

**`POST /api/sync {repo, root, paths?[]}` (optimization, not a dependency).**
- `paths` given (agent's change set) → reindex exactly those (+ delete missing). Moves cost
  off the next read. Hot path for AI batches.
- `paths` omitted → compute delta via `git diff cursor..HEAD` ∪ `git status` (hook path).

**Concurrency.** Per-repo lock (`pg_try_advisory_lock(hash(repo))` or in-process mutex): one
sync runs; concurrent reads wait briefly or serve current index. Prevents double-parse / edge
races.

**Cold start / "first load".** First retrieval on an un-indexed repo (no `repo_state`) →
one-time full `indexPath`, store `cursor=HEAD`. Index is durable in Postgres/pgvector.

### Schema additions
- `repo_state(repo PK, root, cursor_sha, last_synced_at, indexing bool)` — cursor + lock flag.
- `code_files(repo, file_path, content_hash, PRIMARY KEY(repo,file_path))` — per-file hash to
  skip identical files and support the non-git fallback.

### Config — `app.codemem.sync.onRead`
| Mode | Behaviour | Use |
|---|---|---|
| `git` **(default)** | git diff/status gate → reindex delta → serve (self-healing) | our git repos |
| `explicit` | never auto-sync; rely on `/api/sync` + hooks (lowest read latency) | CI/batch |
| `hash` | non-git: throttled hash-scan (mtime+size prefilter → hash) with a short **TTL** | non-git trees |
TTL lives **only** in `hash` mode as a scan throttle — never as the git-mode correctness rule;
committed deltas always sync.

### Real-world alignment (why this is right)
- **Sourcegraph/SCIP** = recompute on a change-set boundary → our commit/transaction sync.
- **Cursor** = content-hash (Merkle) skip-unchanged → our `code_files` hash (GAP B fix).
- **Aider/LSP** = live/per-keystroke → **deliberately dropped** (wrong consumer; we do bulk).
- Uniquely ours: server-side, per-agent-scoped, hybrid+graph, multi-language-precise store.

### MVP (build order)
1. `repo_state` + `code_files` (hash) migrations; record hash in `upsertFile`, clear in `deleteFile`.
2. Make `indexPath` **hash-aware** (skip unchanged) — closes GAP B.
3. **Auto-sync-on-read (`git` mode)** with advisory lock + cold-start full index — closes GAP A/C.
4. `POST /api/sync {paths?}` with git-delta fallback + batch `resolvePendingEdges` once.
5. Git hook installer (`scripts/`) for post-commit/merge/checkout/rewrite.
6. (later) GAP D: PageRank over `code_edges` as a ranking signal.

### Design decisions
- **Dense vs structural ranking:** keep current vector+FTS+RRF; add PageRank later (GAP D). Not blocking.
- **Live modes (`watch`/`on-read` continuous):** dropped — unnecessary for bulk AI editing.

---

## Task 3 — Create a RAG learning repo (accumulate learning materials)

**Goal:** a browsable knowledge base so any part of either app can be looked up. No
duplication: shared concepts live once in a **general concepts** section; app docs **link**
to them (e.g. rag-app's chunking section links to `concepts/chunking.md`).

**Proposed repo layout:**
```
rag-learning/
  README.md                      # index / how to navigate
  concepts/                      # GENERAL, reusable — single source of truth
    chunking.md                  # what a chunker is, strategies, why/how
    tika-document-parser.md      # Apache Tika parsing, formats, how it works
    embeddings.md                # dense vectors: what/why/how, dimensions
    pgvector.md                  # pgvector internals, HNSW, cosine distance
    hybrid-search-rrf.md         # vector + full-text + Reciprocal Rank Fusion
    locator-search.md            # symbol locators, read-fresh-bytes model
    structural-ranking.md        # tree-sitter symbol graph, PageRank/usages
    incremental-indexing.md      # git-hash/Merkle sync, update+delete
    rbac-retrieval.md            # aclTags / roles filtering
    guardrails.md                # PII redaction, prompt-injection, output filter
    redis-caching.md             # semantic cache, hot-tier (RAM vs disk analogy)
    retrieve-only-vs-chat.md     # why kiro is the only LLM brain; token savings
  real-world/                    # closest matches + their approach
    aider-repomap.md             # locator / repo-map, tree-sitter, token efficiency
    cursor-indexing.md           # Merkle-tree incremental index, docs vs code
    continue-dev.md              # @docs (static) vs @codebase (dynamic)
    sourcegraph-cody.md          # code graph / SCIP, keyword+graph over embeddings
    copilot-workspace.md         # @workspace index
    llamaindex-router.md         # multi-index + RouterQueryEngine pattern
    comparison-table.md          # our schema vs each (the table we built)
  apps/
    rag-app.md                   # static/doc: general purpose, arch decisions, e2e API,
                                 #   how it works → links to concepts/* (no re-explaining)
    code-memory.md               # locator index: purpose, arch, indexer, /retrieve, sync
    two-app-architecture.md      # rag-app + code-memory combined schema, inbound/outbound,
                                 #   RAM/disk tiering
  decisions/                     # ADRs
    adr-001-two-rag-split.md
    adr-002-locators-for-code.md
    adr-003-structural-vs-dense.md
    adr-004-retrieve-only.md
```

**Authoring rule (no duplication):** app pages describe *this app's* choices and flow, and
**link** to `concepts/<x>.md` for the generic explanation.
Example: `apps/rag-app.md` → "Uses a chunker ([what/why/how](../concepts/chunking.md))
configured as …". The *definition* of chunking lives only in `concepts/chunking.md`.

**Must-have lookups (acceptance):** I can find, each on its own page/anchor:
chunker · TikaDocumentParser · locator search · pgvector · hybrid/RRF · dense vectors ·
structural ranking · incremental indexing · RBAC retrieval · guardrails · Redis caching ·
retrieve-only vs chat · and each real-world match's approach.

**Seed content:** port the two existing briefings as starting material —
- `rag-app` review findings + endpoint map → `apps/rag-app.md`
- `batch-manager-briefing/` style depth as the template for structure
- the real-world comparison table we built → `real-world/comparison-table.md`.

---

### Status
- [x] Task 1 — `rag-app`: `/retrieve` endpoint — done (rate-limited; 86 tests green)
- [ ] Task 2 — `code-memory`: git-based auto-sync (read-gate + hooks + `code_files`/`repo_state`
      hash; locators already done). MVP steps in §Task 2.
- [ ] Task 3 — `rag-learning` repo (concepts-linked, no duplication)
