# MCP capabilities, providers, and integration profiles

## Resolution contract

Agents bind logical capability IDs. Runtime resolves:

`AgentVersion capability → per-agent provider selection → enabled provider binding → adapter/transport → remote operation`.

Provider connection details and encrypted credentials are organization/user configuration, not AgentVersion fields. Multiple named profiles may exist for the same integration kind; the agent version selects the profile for each capability.

## Supported integration schema categories

Database forms differ from HTTP, Redis, models, Slack, Teams, Kafka, Azure Service Bus, browser, email, and generic MCP. Form schemas must be loaded from organization database configuration, not duplicated in React.

## Web and browser behavior

HTTP and browser adapters default to `allowedHosts=*`, meaning every public HTTP(S) hostname. Both resolve DNS and reject loopback, RFC1918/site-local, link-local, IPv6 local/ULA, IPv4-mapped private addresses, wildcard bind addresses, and metadata endpoints resolving into those ranges. Browser subresources are validated too. Explicit restricted host lists remain supported.

## Tool safety

Every capability includes risk metadata. Side-effecting calls require policy/approval. Enforce schema, timeout, size, method, and result limits in the gateway. Propagate tenant, user, scopes, run ID, deadline, and budget. Record provider, transport, cache status, and outcome without secrets.

## Registry artifacts

MCP manifests describe protocol, transport, implementation artifact, configuration keys, tools, risk, and security. Import tools into staging first; an administrator publishes selected tools as logical capabilities and binds a configured provider. Never auto-expose every discovered tool.
