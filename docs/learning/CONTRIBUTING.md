# Contributing — authoring & linking rules

**Goal:** find an explanation for *any* part of either app in one hop.

## No duplication
- A generic concept (what chunking is, how pgvector works, etc.) is explained **once** in
  [`concepts/`](concepts/).
- App pages ([`apps/`](apps/)) describe only **this app's choices and flow**, and **link**
  to the concept page for the generic explanation.
- Example: `apps/sestacon.md` says *"uses a chunker ([what/why/how](concepts/chunking.md))
  configured for prose"* — it does **not** re-explain chunking.

## Cross-links ("locator links")
- Every page ends with a **## Related** section linking to adjacent pages.
- Use **relative** links so navigation works offline. Patterns (shown literally):
  - concept → concept: `[name](name.md)`
  - app → concept: `[name](../concepts/name.md)`
  - app → real-world: `[name](../real-world/name.md)`
  - real example, from an app page: link text "chunking" → target `../concepts/chunking.md`
- Link on first mention of a term, too (inline), not only in Related.

## Page shape
Concept pages: **What it is · Why · How it works · In our apps (links) · Related.**
App pages: **Purpose · Architecture decisions · E2E API / flow · Components (link out) · Related.**
Real-world pages: **What it is · Approach · What we borrow · Related.**

## Layout
```
concepts/   general, reusable — single source of truth
real-world/ closest external systems + their approach
apps/       Sestacon, code-memory, combined architecture
decisions/  ADRs
```
