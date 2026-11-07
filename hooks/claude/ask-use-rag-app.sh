#!/usr/bin/env bash
# Claude Code PreToolUse hook (opt-in). Fires before a native Grep/Glob.
# It does NOT block — it asks the agent to confirm, nudging it to answer conceptual
# "what / why / which / convention" questions from rag-app (grounded in ingested docs,
# with sources) rather than from raw grep. Searching for a literal string, code, logs,
# or config is legitimate — confirm and proceed.
#
# Claude passes the tool call as JSON on stdin; we only need to emit a decision on stdout.
# See https://docs.anthropic.com/en/docs/claude-code/hooks for the contract.

cat <<'JSON'
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "ask",
    "permissionDecisionReason": "If this is a conceptual / 'what-why-which / convention' question, answer it from rag-app (POST /api/retrieve, grounded in ingested docs with sources) instead of grep. If you're searching for a literal string, code, logs, or config, confirm and proceed. See AGENTS.md."
  }
}
JSON
exit 0
