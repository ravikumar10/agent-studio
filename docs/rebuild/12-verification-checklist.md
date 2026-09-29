# Reconstruction verification checklist

## Static and unit checks

```bash
cd src
mvn test
cd ui/studio-web
npm install
npm run build
npm test
```

Running these through Docker is acceptable when host Java/Node are absent. Also run `git diff --check`.

## Local stack

```bash
./start.sh
./start.sh --status
curl -fsS http://localhost:8080/
```

Confirm control-plane Flyway completion, runtime readiness, Redis/PostgreSQL health, and the local runtime-worker image.

## Acceptance walkthrough

1. Reload Studio and confirm organization-scoped data survives.
2. Add and validate a model connection; ensure keys remain masked.
3. Create/configure an agent, bind ordered skills/capabilities and named profiles, publish a new version.
4. Run it from the workspace; see only conversation/final response there.
5. Open Runs, select the full run ID, and confirm planner/tool/model/usage/terminal events are grouped.
6. Stop an active run and verify the terminal state and Docker worker removal.
7. Fetch an arbitrary public HTTP(S) site using HTTP and browser MCP; verify localhost/private targets fail.
8. Delete user-owned models/integrations/agents and verify reload reflects PostgreSQL state.
9. Sync, pull, and promote one sample of each registry artifact type. Confirm the agent appears as a draft, the skill appears in Agent Builder, and MCP capabilities plus a disabled provider appear. Re-pull old MCP artifacts before promotion so referenced JSON Schema is embedded.
10. Configure the promoted MCP provider, verify it, enable its bindings, attach it to an agent version, and confirm tool events carry the same run ID as the final response.
11. Reuse one `sessionId` for two messages. Confirm PostgreSQL contains turns/evidence, Redis can be deleted without losing context, another subject/agent cannot claim the session, and Runs shows `session.context.loaded` plus `session.turn.persisted`.
12. Publish one agent as an API and widget. Verify the opaque endpoint, origin rejection, optional API-key rejection/success, unique run IDs, session reuse, iframe rendering, and file picker for a document-capable version.
13. Confirm `agent.configuration.frozen` contains the pinned version, initial prompt reference, model profile, and ordered logical capabilities without secrets.
14. Verify observability across several time windows. Reconcile profile rates → `usage.recorded.costMicros` → summary total → displayed USD. Confirm historical V37 backfill and a new priced run both contribute.

## Known negative tests

- Scheduled configuration must not be reported as operational until Temporal schedules create real runs.
- Multi-agent must not be reported as operational until member agents generate correlated child runs.
- Kubernetes must not be reported applied unless a real adapter returns cluster state.
- A public widget is not authenticated merely because it has an opaque URL; production publication needs rate limits and abuse controls.
- Redis loss must not erase durable session history; PostgreSQL loss cannot be repaired from Redis.

## Final handoff

Record changed files, migrations, tests, Docker verification, known limitations, commit SHA, and branch. Ensure `./start.sh` remains the supported clean-machine entrypoint.
