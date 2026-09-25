# 11 — Security Specification

## Identity
OIDC/OAuth2 abstraction supporting Entra ID, Okta, Keycloak and standards-compatible providers. Core fields: subjectId, tenantId, roles, groups, scopes, delegatedTokenRef.

## Identity propagation
Prefer on-behalf-of/delegated downstream authorization where supported; avoid universal superuser service accounts.

## Authorization
RBAC for coarse Studio permissions; ABAC/policy engine for runtime decisions: agent/capability/user/environment/risk/approval.

## Secrets
SecretProvider abstraction: Kubernetes Secrets, Vault, cloud secret managers. Persist `secretRef`, never secret values.

## Tool policy
Risk class, required scopes, data classification, tenant/environment allowlists and approval policy.

## Client runtimes
Authenticate via workload identity/mTLS/OAuth client credentials. Prefer outbound registration/control where inbound private-network exposure is undesirable.

## Network
TLS, namespace/service NetworkPolicy, gateway egress allowlists, no arbitrary internet from managed workers by default.

## Prompt injection
Treat documents/tool output as untrusted. Capability allowlists and side-effect policies cannot be overridden by model instructions.

## Audit
Configuration, releases, permissions, provider registration, tool calls, approvals and remote agent invocation are auditable/tamper-resistant.
