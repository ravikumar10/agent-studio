# 12 — Observability

Use OpenTelemetry.

## Trace hierarchy
Run -> workflow/request -> agent invocation -> model generation / delegated task / tool invocation / policy decision / approval wait.

## Required attributes
Tenant, run/task/agent/version, runtime type, model profile/provider where allowed, capability/provider, environment, token usage, cost estimate, latency, outcome/error. Never attach secrets or unrestricted raw prompts/tool payloads.

## Metrics
Run counts/status/duration, queue latency, agent/model/tool latency and errors, token/cost, approval wait, capability usage, canary quality/health.

## Logs
Structured JSON and correlation IDs. No chain-of-thought.

## Studio run details
Timeline, agent graph, semantic events, tool calls, approvals, artifacts, usage/cost and errors/retries.
