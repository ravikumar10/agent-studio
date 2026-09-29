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
## Model-cost calculation

The Model Gateway records provider token usage in `usage.recorded` and calculates
`costMicros` from the immutable run's logical model profile:

`input tokens × input USD/1M + output tokens × output USD/1M = micro-USD`

This identity works because one USD contains one million micro-USD. Pricing is
configuration, not inferred from a provider name at execution time. Profiles
store `inputCostPerMillionUsd`, `outputCostPerMillionUsd`, `pricingSource`, and
`pricingAsOf` under `generationParameters`.

Migration `V37__model_pricing_and_usage_cost_backfill.sql` adds source-dated
rates to legacy `gpt-4.1` and `claude-sonnet-4-6` profiles only when pricing was
absent, then recalculates existing zero-cost usage events from their recorded
token counts. Explicitly configured rates are never overwritten.

Operational verification after V37 should reconcile these layers:

1. `model_profiles.spec.generationParameters` contains rates and a source date.
2. `usage.recorded.attributes` contains token counts, model calls, and `costMicros` for priced calls.
3. `/api/v1/observability/summary?range=30d` sums the same `costMicros`.
4. Studio divides micro-USD by 1,000,000 to display USD.

Profiles without a concrete model and explicit rates remain unpriced. Provider invoices are authoritative; the dashboard is an estimate based on recorded usage and configured rates.
