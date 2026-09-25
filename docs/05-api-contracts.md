# 05 — API Contracts

Public control/runtime API: REST + JSON, base `/api/v1`. Breaking changes require a new major API or backward-compatible evolution.

## Control Plane
Agents: create/list/get/update drafts; create/list/get versions; validate/canary/activate/rollback.  
Capabilities: create/list; add providers; register/discover MCP; import OpenAPI.  
Models: create/list model profiles.  
Evaluations: create suite, execute, retrieve results.

## Runtime
- `POST /api/v1/runs` — synchronous or async run.
- `GET /api/v1/runs/{runId}`
- `POST /api/v1/runs/{runId}/cancel`
- `GET /api/v1/runs/{runId}/events`
- `GET /api/v1/runs/{runId}/artifacts`
- approval approve/reject endpoints.

## Internal Agent Invocation API
`POST /internal/v1/agent-invocations`

Request includes runId, taskId, immutable agent id/version, typed input, ExecutionContext and constraints. Response includes structured status/output/artifacts/semantic events/usage metrics.

Never serialize framework-native objects across this interface.

## Streaming
SSE in V1 for Studio event display. Semantic events: run.started, agent.started, task.delegated, tool.started/completed, approval.required, agent.completed, run.completed/failed. Never stream hidden reasoning.
