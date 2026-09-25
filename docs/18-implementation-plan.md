# 18 — Implementation Plan for Codex

Do phases in order and satisfy acceptance criteria before advancing.

## Phase 0 — Foundation
Maven multi-module, Java 21, React/TS, lint/format/test conventions, Dockerfiles, local Compose, Helm skeleton, CI.  
**Acceptance:** all modules compile/tests; UI builds; dependencies start; no framework leakage into core.

## Phase 1 — Core + Control Plane
AgentDefinition, immutable AgentVersion, Capability, ModelProfile, PostgreSQL/Flyway, tenant-aware APIs.  
**Acceptance:** schema validation, immutability and active-version pointer tests.

## Phase 2 — Runtime core
Run API, AgentResolver/version pinning, ExecutionContext, policy/budget skeleton, semantic events, Config adapter with mock model.  
**Acceptance:** run pins version; cancellation/budgets work outside prompts.

## Phase 3 — Model Gateway
ModelProvider SPI, profile resolution, one real + one mock/local provider, structured output, usage/cost, timeout/fallback.  
**Acceptance:** agents reference profile only; provider changes need no AgentVersion code changes.

## Phase 4 — Capability + MCP Gateway
MCP registration, Streamable HTTP client, discovery/staging/publish, risk/policy, invocation/audit, test MCP server.  
**Acceptance:** logical capability binding; no MCP URL in AgentVersion; read/write policy tests.

## Phase 5 — Temporal
AgentRunWorkflow, InvokeAgentActivity, approval, cancel/retry, crash/restart test.  
**Acceptance:** killed worker resumes; version remains pinned.

## Phase 6 — Embabel adapter
Implement `runtime-embabel`; sample Shipment Investigation agent.  
**Acceptance:** no Embabel deps in core/control; governed Tool Gateway; internal result contract.

## Phase 7 — Remote/A2A
Remote HTTP, A2A v1 adapter, Agent Card import, skill mapping.  
**Acceptance:** remote Python example invoked; no A2A DTO leakage.

## Phase 8 — Studio UI
Agents/editor, capability catalog, playground, releases, runs, client runtimes.  
**Acceptance:** onboard/release/run sample without direct DB/config editing.

## Phase 9 — Client-hosted runtime
Edge chart, registration/heartbeat, workload identity, local capability access, telemetry.  
**Acceptance:** central Studio invokes an agent in a second simulated cluster/network.

## Phase 10 — Security/observability hardening
OIDC, RBAC/ABAC, secret refs, OTel, audit.

## Phase 11 — Evaluation/release automation
Eval suites, regression, canary, promotion and rollback.

For every phase: update ADR if architecture changes, implement smallest vertical slice, add tests, and never silently violate earlier acceptance criteria.
