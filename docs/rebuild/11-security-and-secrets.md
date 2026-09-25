# Security and secrets

## Identity and tenancy

Tenant, user, and scopes must enter at the API boundary and propagate through agent, model, tool, remote-agent, memory, run, and deployment operations. Every database query is tenant constrained. Production replaces development headers with authenticated OIDC claims and RBAC/ABAC policy.

## Secrets

API keys and credentials are submitted only to backend endpoints, verified before activation where possible, encrypted at rest with `AGENT_STUDIO_ENCRYPTION_KEY`, masked in responses, and redacted from logs/events. Agent definitions contain logical references, never plaintext credentials.

## Network and tool security

Public web wildcard access does not mean unrestricted networking. Block private, local, link-local, metadata, and unsafe schemes after DNS resolution; validate browser subresources and redirects. Bound response sizes/timeouts/actions. Read-only database tools enforce SELECT/bind variables/schema allowlists/row limits. Side effects require risk classification and approval.

## Isolation

Untrusted custom code runs in disposable containers/pods with non-root identity, bounded resources, restricted filesystem/network, and cleanup. Docker access is mediated by the socket proxy. No arbitrary code is dynamically loaded into shared JVMs.
