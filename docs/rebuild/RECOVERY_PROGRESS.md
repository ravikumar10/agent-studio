# Recovery progress — latest implementation handoff

This file is a secret-free checkpoint for continuing Agent Studio in a new Codex or Claude Code session. Revalidate it against `git status`, migrations, and the running stack before editing.

## Repository checkpoint

- Date: 2026-09-30 (Asia/Kolkata)
- Branch: `feat/newrelic-document-ocr-mcp`
- Last committed SHA: `fc2fa9c`
- Working tree: contains intentional uncommitted implementation and documentation changes; do not discard or overwrite them.
- Current focus: public agent endpoints/widgets, durable conversational context, immutable run configuration snapshots, and restored observability cost.

## Latest implementation slices

### Public agent endpoints and widgets — V35

- `AgentRuntimeConfigurationController` persists API/widget publishing configuration with the agent version.
- `PublicAgentController` exposes describe/invoke/poll/widget routes using opaque public IDs.
- API-key mode stores only a SHA-256 verifier; widgets require public mode.
- Allowed origins drive CORS and CSP `frame-ancestors`.
- Document-capable widgets accept image/document attachments and submit bounded base64 input.
- Nginx routes `/widget/` to control-plane.

### Durable agent sessions — V36

- PostgreSQL tables `agent_sessions`, `session_turns`, and `session_evidence` are authoritative.
- Redis key `session-context:{tenant}:{session}` is a disposable TTL projection.
- Session ownership is tenant + agent + subject scoped; legacy sessions can be claimed once.
- Runtime persists sanitized user/assistant/error turns and content-hashed tool evidence.
- Planner/synthesis receives bounded prior context and may reuse grounded evidence.
- Runs emit `agent.configuration.frozen`, `session.context.loaded`, and `session.turn.persisted` events.

### Model cost restoration — V37

- Model pricing is stored under profile `generationParameters` with rate, source, and source date.
- Legacy GPT-4.1 and Claude Sonnet 4.6 profiles are populated only when both rates were absent.
- Existing zero-cost usage events are recalculated from recorded input/output tokens.
- Verification on the retained local database returned 149 calls, 559,114 input tokens, 70,922 output tokens, and 2,741,193 micro-USD for the 30-day window.

## Migrations added in the working tree

| Migration | Purpose | Upgrade verification |
|---|---|---|
| `V35__agent_public_exposures.sql` | Published endpoint/widget configuration | Applied successfully |
| `V36__durable_agent_sessions.sql` | Sessions, turns, evidence, legacy backfill | Applied successfully |
| `V37__model_pricing_and_usage_cost_backfill.sql` | Pricing and historical usage cost | Applied successfully; Flyway schema at 37 |

Never modify these migrations after they have been applied. Use V38 or later.

## Verification performed in the latest session

```text
docker compose -f src/deploy/compose/compose.yml up -d --build control-plane
PASS — Java 21 Maven build and control-plane image completed.

docker compose -f src/deploy/compose/compose.yml logs --tail=120 control-plane
PASS — Flyway validated 37 migrations and applied V37.

docker run ... maven:3.9-eclipse-temurin-21 mvn -B test
PASS — full seven-module reactor; 20 tests, 0 failures, 0 errors.

docker compose -f src/deploy/compose/compose.yml build studio-web runtime-service
PASS — TypeScript/Vite UI production build and Java runtime image build.

npm test (isolated Node 22 container)
NOT RUNNABLE AS A SUITE — Vitest is configured but no test files exist; exits with code 1 and "No test files found".

GET /api/v1/observability/summary?range=30d
PASS — non-zero token/call/cost aggregates returned.

git diff --check
PASS.
```

The clean-database Flyway path, Redis-loss session recovery test, public endpoint/widget acceptance path, complete `./start.sh` readiness check, and creation of actual UI test files still need to be completed before release.

## Known limitations

- Temporal schedules and multi-agent orchestration are not operational.
- Kubernetes manifests are deployment intent until a real cluster adapter applies them.
- Public delivery needs production ingress rate limits, abuse controls, managed identity/key rotation, and organization-specific budgets.
- Session retrieval is recent/bounded; semantic vector retrieval and summarization/compaction remain incomplete.
- Model cost is estimated from configured rates and recorded tokens; provider billing is authoritative.
- A profile without explicit rates remains unpriced.

## Continuation prompt

```text
Read AGENTS.md and docs/rebuild/README.md in the documented order, then read docs/rebuild/RECOVERY_PROGRESS.md. Inspect git status and all uncommitted diffs before editing; preserve them. Verify migrations V35-V37, run the full Maven and React test/build suites, exercise durable session recovery and public endpoint/widget flows, and run ./start.sh readiness. Fix only discovered regressions, update the recovery checkpoint with exact results, and do not claim Temporal schedules, multi-agent execution, or Kubernetes apply are operational.
```
