# Security Policy

## Reporting a vulnerability

Please report security issues privately via
[GitHub Security Advisories](https://github.com/Vedmider/sestacon/security/advisories/new)
rather than opening a public issue. You will get an acknowledgement within a few days.

## Supported versions

The latest release on `main` receives security fixes. Older versions are not maintained.

## Scope notes

Sestacon enforces role-based access control on documents (`X-Roles`/`aclTags`). Reports of
cross-role data exposure, ACL-bypass, or prompt-injection that leaks restricted content are
in scope and treated as high priority. Never include real secrets or restricted documents in
a report — redact them.
