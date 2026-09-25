# Runs, semantic events, SSE, and observability

## Run correlation

One execution has one UUID `run_id`. All lifecycle, planner, MCP/tool, model, cache, usage, failure, cancellation, and completion events use that ID and monotonically ordered sequence values. The Runs page lists executions; selecting one loads `/api/v1/runs/{id}/events` and groups every call in one trace.

The agent workspace displays chat/task input and the final rich response. It intentionally does not duplicate execution-progress and structured-output panels; those belong in Runs.

## Event examples

- `run.created`, `run.started`, `execution.dispatched`
- `agent.analysis.started`, `agent.plan.completed`
- `mcp.pipeline.started`, `tool.completed`, `mcp.pipeline.completed`
- `model.completed`, `agent.analysis.completed`
- `usage.recorded`, `run.completed`, `run.failed`, `run.cancelled`

Events expose semantic state and bounded attributes only—never hidden chain-of-thought or secrets.

## SSE

`/api/v1/runs/stream?tenantId=...` sends run snapshots and run events. UI reconnects and authoritative REST reads recover missed events. SSE is notification, not durable storage.

## Observability

The Observability tab supports 1 minute through 30 day windows, success/failure/active totals, latency, LLM tokens/calls/cost, MCP calls, cache efficiency, model/capability/transport breakdowns, failures, and live activity. PostgreSQL run events are the current source; production should export OpenTelemetry metrics/traces/logs using the same correlation IDs.
