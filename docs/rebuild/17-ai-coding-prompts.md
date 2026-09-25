# Prompts for Codex or Claude Code

Use a fresh branch and one prompt at a time. Require the coding agent to inspect the repository before editing, preserve unrelated work, use patch-based edits, add migrations rather than rewriting applied ones, and report exact verification results. Commit after each accepted phase.

## Session bootstrap prompt

```text
You are reconstructing Enterprise Agent Studio. Read AGENTS.md completely, then docs/18-implementation-plan.md and every docs/rebuild/*.md file in numeric order. The documentation is the requirements source of truth. Inspect the current tree and git status before editing. Preserve existing user changes. Use Java 21 and Spring Boot for services, React/TypeScript for Studio, PostgreSQL/Flyway for durable state, Redis for hot state, and Temporal for durable orchestration. Keep provider/framework/cloud integrations behind adapters. Agents bind logical capabilities and logical model profiles only. Never load arbitrary developer JARs into shared JVMs. Do not expose chain-of-thought. Work only on the requested phase, run its tests, update reconstruction docs when behavior changes, and list changed files, tests, and limitations.
```

## Phase prompts

### Foundation

```text
Implement reconstruction phase 0 only. Create the repository/module skeleton, Java 21 Maven reactor, React/TypeScript/Vite Studio, Dockerfiles, Compose dependencies, health/readiness, CI, and a portable root start.sh requiring only Docker Compose v2. Do not implement business features yet. Verify Maven tests, UI build, Docker build, one-command startup, status/log/stop commands, and volume preservation.
```

### Core and control plane

```text
Implement phases 1 and the control-plane portion of the recovery guide. Define framework-free domain contracts, immutable AgentVersion behavior, tenant-scoped PostgreSQL tables and Flyway migrations, encrypted secret storage, and CRUD APIs for users, agents/versions, logical capabilities, model profiles/connections, provider profiles/bindings, integration types, skills, and registries. Add tests for validation, tenant isolation, immutability, masking, deletion, and active-version lifecycle. Do not add runtime execution yet.
```

### Runtime and model gateway

```text
Implement phases 2 and 3. Add a runtime service with run/event ledger, active-version resolution and pinning, async execution, cancellation, SSE, semantic events, bounded agent loop, logical model profile resolution, mock provider, OpenAI/Anthropic/OpenAI-compatible adapters, backend credential verification, usage/cost capture, timeout/fallback, and one standard rich response envelope. Never persist hidden reasoning. Add contract and service tests.
```

### Capabilities and MCP

```text
Implement phase 4. Add logical capability resolution, named provider profiles and bindings, per-agent provider choice, encrypted credentials, risk/policy checks, audit events, remote invocation contract, and sample HTTP, browser, database, Redis, chart, Slack, and email adapters. Tool evidence must ground factual responses. Enforce read-only SQL and SSRF protections. Keep endpoints out of AgentVersion. Test success, missing/disabled binding, credential failure, policy denial, timeout, and private-network rejection.
```

### Registries

```text
Implement registry-driven extensibility exactly as docs/rebuild/15-registry-promotion.md describes. Add GitHub source registration, Sync, Pull, and Promote states. Enforce safe relative paths and size limits. Promote agents as drafts, skills into the skill catalog, and MCP tools into logical capabilities plus an organization-scoped integration definition generated from JSON Schema. Create providers/bindings disabled. Do not execute or dynamically load repository code. Add API/UI tests and refresh every affected catalog after promotion.
```

### UI

```text
Implement phase 8 against the existing APIs. Build accessible responsive pages for Agents, workspace/chat, Runs, Capabilities/integrations, Model profiles, Registries, Deployments, Memory, Observability, and User profile. All state must reload from the backend after bootstrap and mutations. Use schema-driven forms and dropdowns for selections. Keep run traces out of chat and group them by full run ID in Runs. Render one contextual Markdown response with tables, code, multiple charts/images/files/citations. Verify dialogs scroll and align at laptop and mobile widths.
```

### Docker/Kubernetes lifecycle

```text
Implement execution placement and lifecycle. Support AUTO, IN_PROCESS, DOCKER, and stored KUBERNETES intent without putting cloud SDK types in core contracts. Build the runtime worker image during ./start.sh. Create isolated Docker workers through the restricted socket proxy and remove them on completion, failure, cancellation, timeout, and workspace closure. Generate portable deployment YAML and store immutable deployment plans. Do not claim cluster apply without a real adapter and returned cluster state.
```

### Temporal schedules and multi-agent

```text
Implement Temporal orchestration before enabling scheduled or multi-agent execution. Add deterministic workflows and I/O activities, schedules, retry/cancel/approval behavior, parent/child correlated runs, shared root budget, ordered/parallel member policies, aggregation, crash/restart tests, and semantic events. Keep Temporal types out of core-domain and external I/O out of workflow code. Change UI labels from planned to operational only after acceptance tests pass.
```

### Hardening and release

```text
Implement phases 10 and 11: OIDC, RBAC/ABAC, tenant membership enforcement, workload identity, external secret references, OpenTelemetry, structured logs, audit retention, cost/rate/tool budgets, SBOM/signing/scanning/provenance, evaluation suites, canary promotion, rollback, backup/restore tests, and upgrade compatibility. Produce operator and incident runbooks. Do not weaken local developer mode to implement production security.
```

## Review prompt after every phase

```text
Review the current phase as a skeptical maintainer. Compare implementation to AGENTS.md and docs/rebuild. Run relevant tests and git diff --check. Identify false claims, hard-coded catalog data that should be organization configuration, secret leakage, cross-tenant queries, mutable version behavior, raw endpoint leakage, unsafe SSRF/SQL, missing cleanup, and UI state not refreshed from the backend. Fix in-scope defects, update docs, and provide evidence. Do not expand into the next phase.
```

## Recovery continuation file

At the end of each coding session, update a tracked `docs/rebuild/RECOVERY_PROGRESS.md` containing:

- phase and acceptance criteria completed;
- commit SHA and branch;
- changed migrations and their checksums;
- exact successful/failed commands;
- remaining blockers and known limitations;
- next prompt to run;
- no secrets.

This lets a different coding agent continue without relying on chat history.
