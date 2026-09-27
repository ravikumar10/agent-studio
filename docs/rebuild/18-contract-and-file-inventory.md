# Contract and file inventory

This inventory prevents a clean-room rebuild from omitting a service boundary or silently merging responsibilities.

## Maven and application modules

| Module | Responsibility | Must not contain |
|---|---|---|
| `libs/core-domain` | Agent, version, capability, logical model domain and validation | Spring, MCP, A2A, Temporal, cloud SDK, provider DTOs |
| `libs/runtime-api` | Stable execution request/context/result and runtime adapter SPI | Vendor endpoints and framework-specific agent objects |
| `services/control-plane` | Tenant catalogs, profiles, versions, registries, deployment intent, secrets, Flyway | Agent execution loops or arbitrary extension loading |
| `services/runtime-service` | Runs, SSE, placement, model/tool resolution, adapters, memory, observability | UI concerns or direct mutation of immutable versions |
| `services/slack-mcp` | Slack verify/read/send and formatted report/chart delivery | Platform domain ownership |
| `workers/sample-catalog-worker` | Sample agent, database, and website out-of-process contracts | Shared control-plane code loading |
| `workers/browser-mcp` | Rendered browser navigation, extraction, actions, SSRF controls | Tenant catalog persistence |
| `ui/studio-web` | Organization-scoped Studio UX | Durable browser-local system of record |

Embabel, LangGraph, CrewAI, custom runtimes, and A2A remain adapters behind `runtime-api`; none is the platform core.

## Local Compose services

The minimum development topology includes `postgres`, `redis`, `control-plane`, `runtime-service`, `studio-web`, `docker-api-proxy`, the build-only/tagged `docker-agent-worker`, `sample-data`, `sample-agent-worker`, `web-reader-tool`, `database-reader-tool`, `browser-mcp`, `slack-mcp`, and `example-worker`.

Only Studio publishes port 8080 for normal UI/API access. PostgreSQL may publish 5432 for development. Nginx proxies `/api` to the appropriate backend and serves the SPA. Runtime-to-Docker access goes through a restricted socket proxy; do not mount an unrestricted Docker socket into runtime-service.

## Required API groups

All public APIs use `/api/v1`, carry `X-Tenant-Id`, and user-owned mutations also carry `X-User-Id` until production identity replaces development headers.

| Group | Required operations |
|---|---|
| Studio bootstrap | `GET /studio/bootstrap` |
| User profile/config | get/update profile; list/put/delete namespaced configuration |
| Agents | list/create/update/delete; list/create versions; lifecycle action |
| Runtime config | get/put version placement, trigger, resources, capability profiles |
| Models | verify/create/list/delete connections; list available models; create/list/delete logical profiles |
| Capabilities | list/create logical capabilities |
| Providers | list/create/update/delete/verify; list/create/delete bindings |
| Integration types | list organization-scoped schema definitions |
| Skills | list available skills |
| Registries | list/create/delete; sync; list artifacts; pull; promote; delete artifact |
| Deployments | environments, plans, apply adapter, schedules, Brain profiles |
| Runs | list/start/get/cancel/events; SSE stream |
| Memory | put/get/delete namespaced entries |
| Observability | summary by bounded time range |

The exact Java mappings are discoverable with:

```bash
rg -n '@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping)' \
  src/services/control-plane/src/main/java \
  src/services/runtime-service/src/main/java
```

## Flyway history

Recreate and retain these migration names and ordering for upgrade compatibility:

| Version | Purpose |
|---|---|
| V1 | control-plane core agents, versions, capabilities, model profiles |
| V2 | registries and memory |
| V3 | registry artifacts |
| V4 | sample agents |
| V5 | execution contract and run/event persistence |
| V6 | model connections |
| V7 | database MCP and skills |
| V8 | Agent Creator |
| V9 | user profiles and encrypted configuration |
| V10 | integration ownership and sync state |
| V11 | remove credentials from model profiles |
| V12 | deployment environments, schedules, Brain |
| V13 | Kubernetes capability risk corrections |
| V14 | capability providers and bindings |
| V15 | local transform bindings |
| V16 | per-version runtime configuration |
| V17 | typed integration profiles |
| V18 | organization runtime bootstrap |
| V19 | HTTP and browser MCP capabilities |
| V20 | organization integration catalog |
| V21 | expanded local web allowlists |
| V22 | content and chart capabilities |
| V23 | repaired web provider bindings |
| V24 | email MCP capabilities |
| V25 | public-web wildcard defaults with SSRF defense |
| V26 | Slack capabilities |
| V27 | Slack provider bindings |
| V28 | local Slack MCP routing |
| V29 | registry promotion lifecycle |

Do not edit these after release. Add V30 and later for subsequent changes.

## Required durable concepts

At minimum preserve tables/relations for organization accounts and users; agents and immutable versions; release state; model connections and logical profiles; capabilities, provider profiles, provider secrets, and bindings; agent runtime configuration and per-capability provider selection; registries and artifacts; available skills; runs and run events; memory records; deployment environments/plans; schedules and Brain profiles; organization integration types; and encrypted user secrets/configuration.

Every row that represents organization data needs an organization/tenant key. Foreign keys and uniqueness constraints must include it where appropriate.

## Registry repository contract

`catalog.json` is metadata. It points to versioned JSON manifests and optional implementation/configuration assets. It must identify artifact type (`AGENT`, `MCP`, or `SKILL`), ID, version, name, description, and safe repository-relative path.

An MCP manifest declares tools with logical names, descriptions, input/output schemas, side-effect/risk metadata, transport, implementation image/source, and optional configuration schema reference. The schema uses standard JSON Schema properties; `writeOnly: true` marks encrypted credential fields. The registry may contain source code and Dockerfiles, but publication metadata never means code is trusted or running.

An agent manifest declares identity/version, interface, topology, trigger intent, logical model profile, ordered capabilities, and skills. A skill manifest supplies human-readable instructions/reference metadata and must not embed secrets.

## Configuration and secret rules

- Organization integration definitions are server-provided JSON and rendered dynamically.
- Multiple named profiles may exist for the same integration kind.
- Agent versions select named profiles per logical capability.
- Secret values are encrypted at rest and returned only as masked/presence status.
- Provider verification is backend-driven; enabling and binding happen only after successful configuration.
- Never serialize credentials into agent specs, events, logs, prompts, registry snapshots, or Kubernetes YAML.

## Essential files for a source archive

Before declaring a backup complete, verify it contains:

```text
AGENTS.md
start.sh
docs/**/*.md
src/pom.xml
src/**/pom.xml
src/ui/studio-web/package.json
src/ui/studio-web/src/**
src/services/**/src/main/**
src/services/**/src/test/**
src/libs/**/src/**
src/workers/**
src/deploy/compose/compose.yml
src/deploy/docker/**
src/deploy/helm/**
src/services/control-plane/src/main/resources/db/migration/**
src/contracts/**
src/examples/github-registry/**
```

A source archive without migrations, the launcher, Compose/Dockerfiles, or contracts is not a recoverable application.
