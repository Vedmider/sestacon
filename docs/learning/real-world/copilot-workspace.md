# GitHub Copilot — @workspace

## What it is
Copilot Chat's `@workspace` (and Copilot Workspace) answer questions using an index of the
whole project.

## Approach
- Builds a workspace index blending **embeddings + lexical** signals, maintained by the IDE.
- Retrieves relevant snippets to ground chat/edits; a single blended store (context is
  tagged, not split into separate deployments).

## What we borrow
A contrast point: Copilot uses **one blended store**. Our
[two-app split](../apps/two-app-architecture.md) trades a little infra for cleaner
ops/scaling/RBAC and a stable shared doc tier — worthwhile when the static KB is shared
across repos/teams.

## Related
[Two-app architecture](../apps/two-app-architecture.md) · [Comparison table](comparison-table.md)
