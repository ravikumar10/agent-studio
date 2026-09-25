# 20 — Sources and Version Notes

Validated on **2026-09-23**. The implementation spec is deliberately self-contained for offline Codex use.

## MCP / Spring AI
- https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html
- Spring AI observed: **2.0.1**.
- Supports MCP client/server and STDIO, Streamable HTTP, Stateless Streamable HTTP and SSE transports.

## A2A
- https://a2a-protocol.org/latest/specification/
- https://a2a-protocol.org/latest/whats-new-v1/
- Treat **A2A v1.0** as remote-agent interoperability baseline.
- Core supported bindings include JSON-RPC, gRPC and HTTP+JSON.

## Temporal
- https://docs.temporal.io/
- Used for durable workflow execution, not agent reasoning.

## Embabel
- https://github.com/embabel/embabel-agent
- https://docs.embabel.com/embabel-agent/
- JVM-native agent framework; MCP integration exists.
- Its A2A capability remains framework-specific and must stay behind adapter boundaries.
- Do not use SNAPSHOT dependencies in production.

## Dependency policy
At implementation time choose stable compatible releases, pin them in dependency management, record major upgrades as ADRs, and preserve adapter boundaries regardless of framework improvements.
