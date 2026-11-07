# Hard enforcement (opt-in) — Claude Code

The usage policy in [`../../AGENTS.md`](../../AGENTS.md) is advisory: an autonomous agent *should*
answer conceptual "what / why / which" questions from rag-app (grounded in your docs) rather than
guessing from raw `grep`. If you observe it skipping rag-app, this adds a **speed bump** — not a cage.

## What it does

Registers a Claude Code **`PreToolUse`** hook on the native **`Grep`** and **`Glob`** tools. Before
either runs, the hook returns a `permissionDecision: "ask"` — Claude pauses and asks you to confirm,
with a reason: if the question is conceptual, answer it from rag-app's `/api/retrieve` (grounded,
with sources); if it's a literal/code/log/config search, confirm and proceed.

- It **asks**, it does not **block**. This matters more here than for a code index: rag-app only
  knows what's been ingested, and plenty of searches (code, logs, config, literal strings) are
  genuinely not rag-app's job. A hard block would be wrong most of the time. The hook's value is the
  *prompt to reconsider*, not a prohibition.
- It touches only `Grep`/`Glob`. It never reads your content or sends anything anywhere.
- It is Claude-specific: hook schemas and tool names aren't portable across AI clients. Every other
  client still gets the soft policy via `AGENTS.md` and the pointer files.

## Install

Merge `hooks/claude/settings.json` into your Claude Code settings
(`.claude/settings.json` in this project, or your user settings). If you have no hooks yet:

```bash
mkdir -p .claude
cp hooks/claude/settings.json .claude/settings.json
```

If you already have a `.claude/settings.json`, merge the `hooks.PreToolUse` array by hand rather than
overwriting.

## Remove

Delete the `PreToolUse` entry you added (or remove `.claude/settings.json` if the hook was its only
content). Nothing else is left behind; the script in this folder is inert unless referenced by
settings.
