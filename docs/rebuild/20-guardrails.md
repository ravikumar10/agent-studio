# Guardrails

Guardrails are organization-scoped reusable controls attached to immutable agent versions. A skill explains how to perform a task; a guardrail defines mandatory safety, privacy, grounding, cost, or tool-use constraints.

## Persistence and API

Create the `guardrails` table with Flyway migration `V34__organization_guardrails.sql`. Each row is keyed by `tenant_id` and `guardrail_id`, with a human-readable runtime instruction and structured JSON configuration. Enforcement modes are `BLOCK`, `WARN`, and `REDACT`; phases are `INPUT`, `TOOL`, `OUTPUT`, and `BOTH`.

Expose tenant-filtered CRUD at `/api/v1/guardrails`. Require `X-Tenant-Id` and return a logical reference such as `guardrail://prompt-injection-defense`. Agent definitions store this reference—not database keys, provider URLs, or Java classes.

## Studio and agent versions

The `Guardrails` tab presents a compact catalog. `New guardrail` and each card's `Edit` action open the same scrollable modal, following the interaction pattern used by model and integration editors. The catalog supports create, edit, enable/disable, and delete. The agent configuration form lists enabled guardrails. Selections are saved in the encoded `plan://` document beside skills and ordered MCP capabilities:

```json
{
  "skills": ["github://organization/registry/skills/reporting/SKILL.md"],
  "guardrails": [
    "guardrail://prompt-injection-defense",
    "guardrail://grounded-tool-response"
  ],
  "order": ["web.fetch", "content.chart"]
}
```

Saving creates a new immutable agent version. Existing runs remain pinned to the version with which they started.

## Runtime resolution

At invocation, resolve references from the tenant database and ignore missing or disabled records. Add resolved instructions to the bounded planning context as mandatory constraints. Never fetch mutable definitions from a remote source during a run.

This implementation supplies declarative controls to the planner. Hard guarantees also require deterministic adapters at the corresponding boundary: input checks before planning, tool policy before MCP invocation, and redaction before persistence or external delivery. Never rely on model compliance alone for secrets, authorization, or side effects.

## Verification

```bash
docker compose -f src/deploy/compose/compose.yml build control-plane runtime-service studio-web
docker compose -f src/deploy/compose/compose.yml up -d control-plane runtime-service studio-web
curl -fsS -H 'X-Tenant-Id: local-development' -H 'X-User-Id: local-user' http://localhost:8080/api/v1/guardrails
```

Open the Guardrails tab, edit a control, attach it to an agent, save a version, and confirm its decoded `plan://` document contains the expected `guardrail://` references.
