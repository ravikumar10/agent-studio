# 16 — Testing and Evaluation

## Software tests
Unit tests for domain rules. Contract tests for internal Agent API, MCP adapter, A2A adapter and public APIs. Integration tests with Testcontainers where practical (PostgreSQL/Redis/mock MCP; Temporal test environment). E2E: create -> validate -> release -> run -> tool -> result -> trace.

## Agent evaluations
Eval case: input, context fixture, structural/business assertions, optional rubric/evaluator, max cost/latency. Prefer deterministic tests before LLM-as-judge.

## Release regression
Compare against active version: pass rate, policy violations, latency, token/cost, tool count, failure rate.

## Security tests
Tenant isolation, scopes, approval enforcement, injection scenarios, SSRF/endpoint validation, secret leakage, unauthorized capability access.

## Resilience tests
Kill worker mid-run, model/MCP timeout, Temporal restart, duplicate retry, client runtime disconnect, remote agent unavailable.
