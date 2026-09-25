# 07 — Capability, Tool and MCP Specification

## Principle
Agent binds `shipment.lookup`, not `https://host/mcp`.

## Resolution
`Capability -> CapabilityProvider -> adapter -> concrete operation`.
Providers: MCP, REST/OpenAPI, gRPC, event command, DB/search, agent.

## Tool Gateway responsibilities
Resolve provider; authenticate/authorize; propagate tenant/user; validate request/response schema; enforce risk/rate/policy; invoke; redact/classify; audit/measure; retry only safely.

## MCP baseline
Use Spring AI MCP / official MCP Java SDK in Tool Gateway. Prefer Streamable HTTP for remote production. Support Stateless Streamable HTTP where appropriate. STDIO is for local development/tightly controlled subprocess tools. Maintain SSE only for compatibility.

## MCP registration
Store id/name/owner/environment/transport/endpointRef or commandRef/authProfile/trust/classification/enabled/health. Discovery imports tools to staging; an admin explicitly approves/publishes selected tools as governed Capabilities. Never auto-expose all discovered tools to all agents.

## Risk classes
READ_ONLY, LOW_RISK_WRITE, REVERSIBLE_WRITE, IRREVERSIBLE_WRITE, PRIVILEGED. Default approval for irreversible/privileged operations.

## OpenAPI import
Parse -> select operations -> normalize schemas -> map auth -> assign logical capability IDs -> classify risk -> test -> publish.

## Versioning
Validated AgentVersion uses pinned/compatible capability bindings. Tool implementation may change only under explicit compatibility rules.

Embabel-native MCP support may be used inside specialized workers, but central enterprise governance stays in Tool Gateway.
