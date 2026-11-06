# Guardrails

## What it is
Safety filters around a RAG query: **prompt-injection detection**, **PII redaction**, and
**output filtering**.

## Why
RAG mixes untrusted inputs: the user question *and* retrieved context (which may itself carry
injected instructions — "indirect" injection). Guardrails reduce leakage and manipulation.

## How it works (as in `rag-app`)
- **Prompt-injection detector:** regex patterns ("ignore previous instructions", "reveal
  system prompt", …) — run on the question **and** on retrieved chunks (indirect-injection
  defense). Best-effort, English, bypassable → defense-in-depth, not a guarantee.
- **PII redactor:** regex for email/phone/SSN/credit-card, with **Luhn** check on cards to
  cut false positives; runs on input and output.
- **Output guard:** strips banned phrases before returning.

Note: guardrails matter on the **[/chat](retrieve-only-vs-chat.md)** (LLM) path. On the
retrieve-only dev path there is no LLM, so they are lighter/irrelevant.

## In our apps
- [`rag-app`](../apps/rag-app.md): full guardrail chain on `/chat`.

## Related
[Retrieve-only vs chat](retrieve-only-vs-chat.md) · [RBAC retrieval](rbac-retrieval.md) ·
[`rag-app`](../apps/rag-app.md)
