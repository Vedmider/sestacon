# Contributing to Sestacon

Thanks for helping improve Sestacon. This repo favours small, test-first changes.

## Prerequisites

- JDK 21 (`source env.sh` sets the project's toolchain)
- Docker (for the pgvector/Redis Testcontainers integration tests)

## Build & test

```bash
bash scripts/fetch-model.sh   # one-time: download the local embedding model (~87 MB)
mvn -B clean verify           # unit (surefire) + integration (failsafe + Testcontainers)
```

Black-box probe against a running stack (`bash scripts/dev-local.sh up`):

```bash
python3 e2e/sestacon_probe.py            # all cases
python3 e2e/sestacon_probe.py --only SA25,SA26
```

## Pull requests

1. Write or update a test first; watch it fail, then make it pass.
2. Keep the change focused; match existing style and the minimal-comment convention.
3. For anything touching ACLs, caching, or guardrails, add a regression probe case.
4. Run `mvn -B clean verify` before pushing. CI must be green.

## Reporting issues

Use the issue templates. For anything security-sensitive, see [SECURITY.md](SECURITY.md).
