# LlamaIndex — multi-index + router

## What it is
LlamaIndex (RAG framework) supports **multiple indices** and a **RouterQueryEngine** that
picks which index to query per request.

## Approach
- Build separate indices (e.g. one for docs, one for code), each with its own retriever.
- A router (rule- or LLM-based) dispatches a query to the right index, or fans out and
  merges.

## What we borrow
The **two-store + router** pattern underpinning our design: kiro (the agent) is effectively
the router, calling [`Sestacon`](../apps/sestacon.md) for conventions and
[`code-memory`](../apps/code-memory.md) for code locators, then merging.

## Related
[Two-app architecture](../apps/two-app-architecture.md) ·
[Retrieve-only vs chat](../concepts/retrieve-only-vs-chat.md) · [Comparison table](comparison-table.md)
