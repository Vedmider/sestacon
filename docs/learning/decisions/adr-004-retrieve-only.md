# ADR-004 — Retrieve-only (kiro is the only LLM brain)

**Status:** accepted

## Context
`Sestacon` was built as `ingest → /chat → search → LLM → answer`. For the developer-helper
use case the agent (kiro) is already an LLM.

## Decision
Both apps expose a **[retrieve-only](../concepts/retrieve-only-vs-chat.md)** path
(`/retrieve`, no LLM). kiro is the single LLM; the apps are pure retrieval.

## Rationale
- No second LLM to pay for; retrieval can't edit files/run tools anyway.
- Token savings: return small chunks/[locators](../concepts/locator-search.md), skip
  LLM answer + [guardrails](../concepts/guardrails.md).
- `Sestacon` keeps `/chat` for the original doc-Q&A use case — additive, not a rewrite.

## Related
[Retrieve-only vs chat](../concepts/retrieve-only-vs-chat.md) ·
[Two-app architecture](../apps/two-app-architecture.md)
