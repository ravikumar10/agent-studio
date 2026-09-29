# 12 — Observability

Use OpenTelemetry.

## Trace hierarchy
Run -> workflow/request -> agent invocation -> model generation / delegated task / tool invocation / policy decision / approval wait.

## Required attributes
Tenant, run/task/agent/version, runtime type, model profile/provider where allowed, capability/provider, environment, token usage, cost estimate, latency, outcome/error. Never attach secrets or unrestricted raw prompts/tool payloads.

## Metrics
Run counts/status/duration, queue latency, agent/model/tool latency and errors, token/cost, approval wait, capability usage, canary quality/health.

Model cost is an estimate derived from recorded provider input/output tokens and source-dated per-million-token rates stored in the logical model profile. Persist integer micro-USD in `usage.recorded`; aggregate the same value in the Observability API and convert only for display. Unknown pricing stays explicitly unpriced/zero. Provider billing remains authoritative.

## Logs
Structured JSON and correlation IDs. No chain-of-thought.

## Studio run details
Timeline, agent graph, semantic events, tool calls, approvals, artifacts, usage/cost and errors/retries.

The semantic timeline also includes the immutable configuration snapshot and session-context source/counts, but never raw credentials or hidden reasoning. Public API/widget invocations use the same run IDs and observability pipeline as Studio invocations.
