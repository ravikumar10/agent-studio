# Services and data flow

## Local topology

- `studio-web`: React bundle plus Nginx reverse proxy on host port 8080.
- `control-plane`: catalogs, profiles, registries, deployments, users, agent versions, and Flyway.
- `runtime-service`: run creation, version resolution, placement, agent adapters, tool/model calls, events, SSE, cancellation, and observability.
- `postgres`: durable system of record.
- `redis`: hot memory and bounded exact-result cache.
- `sample-agent-worker`: out-of-process sample/code agent contract.
- `web-reader-tool`: governed HTTP/web extraction adapter.
- `browser-mcp`: Playwright-based rendered-page navigation/extraction/actions.
- `database-reader-tool`: read-only sample database adapter.
- `sample-data`: isolated demonstration PostgreSQL database.
- `docker-api-proxy`: restricted Docker socket surface used by runtime-service.

## Invocation flow

1. Studio or a published endpoint posts `StartRunRequest` to `/api/v1/runs` with tenant/subject context and an optional `sessionId`.
2. Runtime resolves the requested or active immutable `AgentVersion`, validates session ownership, and freezes an observable configuration snapshot for the run.
3. PostgreSQL provides bounded prior turns/evidence; Redis may satisfy the same session projection as a disposable hot cache.
4. A run row and `run.created`, `agent.configuration.frozen`, and `session.context.loaded` events are committed around startup.
5. Placement resolves to in-process, Docker, or Kubernetes intent.
6. The configured model receives the fixed initial prompt, current message, bounded session context, registry-pinned skills, and only the capabilities attached to that agent version.
7. The bounded planner decides which attached tools are useful and in what valid order; it cannot invent or dynamically attach capabilities.
8. Logical capabilities resolve through per-agent provider bindings. Tool outputs become grounded evidence and may be reused from the session when sufficient.
9. The model synthesizes one final response from the current request and grounded evidence.
10. Semantic tool/model/usage/session events are stored with the same `run_id`; assistant/error turns and tool evidence are persisted under the session.
11. Run output/status is persisted and broadcast over SSE.
12. Studio/widget shows the final response; Runs shows the complete trace and Observability aggregates calls, tokens, latency, and cost.

## Public invocation flow

Publishing an agent version stores `agent_public_exposures`. `PublicAgentController` resolves the opaque `publicId`, verifies origin and optional `X-Agent-API-Key`, pins the configured agent version, and forwards the invocation to runtime-service. `/widget/{publicId}` serves a responsive same-origin chat client and exposes an attachment picker only when the pinned agent declares a `document.*` capability. Public calls still use the normal run/session ledger, budgets, provider bindings, guardrails, and observability.

## Dependency rules

Core domain never imports provider, framework, cloud, A2A, or MCP transport DTOs. Runtime adapters depend on the stable runtime API. Tool endpoints are resolved from logical capabilities, never embedded in agent definitions. Model endpoints are resolved from logical profiles, never stored in prompts or AgentVersion contracts.
