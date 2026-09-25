# Scheduling and multi-agent implementation plan

This document is deliberately explicit because the UI currently stores these configurations but runtime execution is incomplete.

## Scheduling current state

Runtime configuration stores trigger type, cron expression, and timezone. Deployment planning can store schedule rows and emit Kubernetes CronJob YAML. No Studio scheduler currently scans configurations and creates runs. Agent descriptions are metadata and are not scheduled input. Cron validation is inconsistent between runtime configuration and deployment planning.

## Required scheduled execution

Use Temporal. On publish/update, reconcile one durable schedule per tenant/agent/version configuration. Store explicit `scheduleInput`, cron, timezone, enabled flag, overlap policy, catch-up policy, timeout, retry policy, and pinned/active-version policy. Each occurrence creates a normal run and events. Default to no overlap. Disable/delete must remove or pause the Temporal schedule idempotently.

Standardize on one cron grammar and validate it in backend and UI. Never silently use description as input; UI may offer “copy description into scheduled prompt” as an explicit action.

## Multi-agent current state

UI stores `MULTI_AGENT` and logical member capabilities such as `agent.<id>.invoke`. Runtime currently ignores `agentCapabilitiesRequired`; no delegation happens.

## Required multi-agent execution

Temporal owns a parent workflow. Persist `root_run_id`, `parent_run_id`, and `child_run_id`. Resolve and pin each member version at delegation time according to policy. Propagate tenant/user/scopes/deadline/budget. Enforce maximum depth, fan-out, concurrency, model/tool budgets, retries, and cycle detection. Child cancellation follows parent cancellation. Aggregate final outputs through a documented response contract.

Runs UI should show one parent trace with expandable member runs. Every child tool/model event retains its child ID and shared root ID. Do not expose chain-of-thought; record delegation intent, selected member, bounded input/output summaries, status, usage, and errors.
