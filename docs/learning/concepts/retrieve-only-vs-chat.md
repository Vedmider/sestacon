# Retrieve-only vs chat

## What it is
Two ways to use a RAG app:
- **/chat:** `embed → search → guardrails → LLM → answer` (RAG calls an LLM, returns prose).
- **/retrieve:** `embed → search → return chunks/locators` (**no LLM**).

## Why it matters
For the developer-helper use case, **kiro is the only LLM brain**. The RAG apps must be
pure retrieval — otherwise you pay for a second LLM and can't edit files or run tools.

Token savings come from retrieve-only:
- return a few small chunks/[locators](locator-search.md), not full files/dirs;
- no LLM answer generated inside RAG;
- [guardrails](guardrails.md)/answer-cache are skipped on this path.

## How it works
Same pipeline up to search, then **stop before the LLM** and project results:
- text projection → static docs ([`rag-app`](../apps/rag-app.md));
- locator projection → code ([`code-memory`](../apps/code-memory.md)).

## In our apps
- [`rag-app`](../apps/rag-app.md): keeps `/chat` for doc-Q&A; adds `/retrieve` for kiro.
- [`code-memory`](../apps/code-memory.md): retrieve-only by design.

## Related
[Locator search](locator-search.md) · [Guardrails](guardrails.md) ·
[ADR-004 Retrieve-only](../decisions/adr-004-retrieve-only.md) ·
[Two-app architecture](../apps/two-app-architecture.md)
