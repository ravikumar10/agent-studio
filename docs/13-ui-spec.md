# 13 — UI Specification

React + TypeScript.

## Navigation
Dashboard, Agents, Capabilities, MCP/API Connections, Model Profiles, Evaluations, Releases, Runs, Client Runtimes, Policies, Administration.

## Agent editor
Identity; runtime/hosting; instructions/prompt ref; schemas; model profile; tool capabilities; delegated agent capabilities; context; memory; budgets; security; hosting/deployment; tests/evals. Actions: Save Draft, Validate, Test, Eval, Create Version, Release.

## Runtime selector
Hosting: Platform Managed / Client Managed / External. Runtime: Config / Embabel / Remote HTTP / Remote A2A. UI enforces valid combinations.

## Capability catalog
Search/filter by capability, owner, risk, provider type, environment, classification. Add logical capability only, never an endpoint URL.

## Playground
Select draft/version, provide typed input/context/test identity; display structured output, semantic timeline, tool invocations, usage/cost, latency, validation errors.

## Release
Version diff, prompt/model/tool/policy changes, eval comparison, schema compatibility, canary state, promote/rollback.

## Runs
List and detail with agent/version, tenant, status, duration, cost, runtime location and semantic event graph.

## Client Runtime
Runtime ID, environment/cluster, health/connectivity, runtime version, supported worker types, allowed capabilities, last heartbeat.
