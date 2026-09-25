# Agent Studio reconstruction guide

This directory is the operational source for rebuilding the current implementation with Codex, Claude Code, or another coding agent. Read `AGENTS.md` first; its architecture rules override convenience choices in these notes.

## Required reading order

1. `00-current-state.md` — what exists, what is intentionally incomplete, and the definition of done.
2. `01-local-runtime.md` — one-command Docker startup and recovery.
3. `02-services-and-data-flow.md` — process boundaries and request flow.
4. `03-database-and-migrations.md` — durable state and migration discipline.
5. `04-agent-catalog-and-builder.md` — agent definitions, immutable versions, and UI creation.
6. `05-models-agent-loop-and-responses.md` — provider onboarding, bounded planning, grounding, and rich responses.
7. `06-mcp-capabilities-and-integrations.md` — logical tools, provider profiles, bindings, web/browser rules, and registry direction.
8. `07-memory-and-caching.md` — Redis hot memory, PostgreSQL cold memory, and tool-result reuse.
9. `08-execution-placement-and-lifecycle.md` — in-process, Docker, Kubernetes intent, cancellation, and cleanup.
10. `09-runs-events-and-observability.md` — run correlation, SSE, cost, calls, and the Runs UI.
11. `10-scheduling-and-multi-agent.md` — current gaps and the required Temporal implementation.
12. `11-security-and-secrets.md` — tenant isolation, encryption, SSRF, policy, and redaction.
13. `12-verification-checklist.md` — exact build and acceptance checks.
14. `13-change-index.md` — feature-to-code/migration lookup for every major increment.
15. `14-slack-mcp.md` — Slack MCP adapter, credentials, tool contracts, ordering, and local deployment.
16. `15-registry-promotion.md` — GitHub discovery, pull, promotion, and safe provider activation.
17. `16-disaster-recovery.md` — clean-room recovery sequence when only the documentation remains.
18. `17-ai-coding-prompts.md` — phase-scoped prompts and checkpoint rules for Codex or Claude Code.
19. `18-contract-and-file-inventory.md` — modules, services, APIs, migrations, and required files.
20. `RECOVERY_PROGRESS.template.md` — resumable, secret-free handoff record for reconstruction sessions.

## Reconstruction command for a coding agent

Use this prompt after cloning the repository:

> Read `AGENTS.md`, `docs/18-implementation-plan.md`, and every file under `docs/rebuild/` in numeric order. Treat them as requirements. Inspect existing code before editing. Implement phases in order, use Flyway for every schema change, preserve immutable agent versions, keep integrations behind adapters, run the verification checklist, and finish by running `./start.sh`.

If the source tree has been lost and only these documents were recovered, start with `16-disaster-recovery.md`. Use one phase prompt at a time from `17-ai-coding-prompts.md`; do not ask a coding agent to recreate the entire platform in one unreviewed pass.

## Truth hierarchy

When descriptions differ, use this order:

1. `AGENTS.md` non-negotiable rules.
2. Machine-readable schemas and API definitions.
3. `docs/rebuild/00-current-state.md` for implemented-versus-planned status.
4. Numbered architecture specifications under `docs/`.
5. Current code and migrations for behavior already implemented.

Do not infer that a visible UI control proves the backend behavior is complete. Scheduling and multi-agent orchestration are explicit examples of configuration that is stored but not yet executed.
