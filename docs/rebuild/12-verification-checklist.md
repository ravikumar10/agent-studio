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
11. Verify observability across several time windows and reconcile totals with run events.

## Known negative tests

- Scheduled configuration must not be reported as operational until Temporal schedules create real runs.
- Multi-agent must not be reported as operational until member agents generate correlated child runs.
- Kubernetes must not be reported applied unless a real adapter returns cluster state.

## Final handoff

Record changed files, migrations, tests, Docker verification, known limitations, commit SHA, and branch. Ensure `./start.sh` remains the supported clean-machine entrypoint.
