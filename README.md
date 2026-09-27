# Enterprise Agent Studio — Implementation Specification

## Run locally with one command

The only host prerequisite is Docker Desktop (macOS) or Docker Engine with
Docker Compose v2 (Linux). Java 21, Maven, Node, PostgreSQL, Redis, the MCP
tools, and all application services are built and run in containers.

```bash
./start.sh
```

Open <http://localhost:8080> after the readiness check completes. Common
operations are `./start.sh --status`, `./start.sh --logs`, and
`./start.sh --stop`. Database and Redis volumes are preserved when stopped.

Status: **spec-led working implementation with local Docker deployment**  
Primary stack: **Java 21 + Spring Boot + Spring AI + Temporal + React/TypeScript**  
Default JVM agent runtime: **Embabel adapter**  
Interoperability: **MCP for tools/resources, A2A for remote agents, stable internal invocation contract**  
Deployment: **Kubernetes-first, cloud-neutral, self-hostable, client-hosted, hybrid and on-prem**

## Product statement
Enterprise Agent Studio is a BriX-inspired, framework-neutral control plane and runtime platform for building, registering, testing, releasing, governing and operating AI agents.

> **Build centrally. Govern centrally. Run anywhere.**

Agents may run on the platform, inside a client/customer environment, or as an independently managed remote agent. Developers bind agents to logical capabilities rather than infrastructure endpoints.

## Read order for Codex
1. `AGENTS.md`
2. `docs/01-product-spec.md`
3. `docs/02-architecture.md`
4. `docs/03-repository-structure.md`
5. `docs/04-domain-model.md`
6. `docs/05-api-contracts.md`
7. `docs/06-agent-runtime.md`
8. `docs/07-capability-tool-mcp.md`
9. `docs/08-a2a-remote-agents.md`
10. `docs/09-temporal.md`
11. `docs/10-data-model.md`
12. `docs/11-security.md`
13. `docs/12-observability.md`
14. `docs/13-ui-spec.md`
15. `docs/14-release-versioning.md`
16. `docs/15-deployment-portability.md`
17. `docs/16-testing-evaluation.md`
18. `docs/17-non-functional-requirements.md`
19. `docs/18-implementation-plan.md`
20. `docs/19-architecture-decisions.md`
21. `docs/20-sources-and-version-notes.md`

Machine-readable contracts are under `schemas/` and `api/`.

For loss recovery or reconstruction with Codex/Claude Code, continue with
[`docs/rebuild/README.md`](docs/rebuild/README.md). It documents the current
implemented state, exact service/data flows, migrations, registries, agent
runtime, MCP/model/memory behavior, Docker/Kubernetes execution, UI, run
traces, security, known scheduling/multi-agent gaps, and verification steps.

If the working tree is lost, begin with
[`docs/rebuild/16-disaster-recovery.md`](docs/rebuild/16-disaster-recovery.md),
then execute one prompt at a time from
[`docs/rebuild/17-ai-coding-prompts.md`](docs/rebuild/17-ai-coding-prompts.md).
The module/API/migration checklist is in
[`docs/rebuild/18-contract-and-file-inventory.md`](docs/rebuild/18-contract-and-file-inventory.md).

## Implementation philosophy
Do **not** build another agent framework. Build a control plane, stable execution harness, framework adapters, governed capability gateway, durable workflow integration, deployment/runtime management, and enterprise governance.
