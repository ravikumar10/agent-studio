# Clean-room disaster recovery

This runbook is for rebuilding Agent Studio when the local source tree is gone. It is deliberately more prescriptive than the architecture documents. It does not replace backups: preserve the Git repositories, container registry, PostgreSQL backups, encryption key, and external secrets separately.

## Recovery inputs

Recover as many of these as possible before generating code:

- this documentation tree and `AGENTS.md`;
- the `ravikumar10/agent-studio` Git history or latest archive;
- the `ravikumar10/agent-studio-sample-registry` registry repository;
- PostgreSQL dump and Redis persistence volume, if runtime state matters;
- `AGENT_STUDIO_ENCRYPTION_KEY`; encrypted database secrets cannot be recovered without it;
- provider credentials, GitHub token, Slack bot token, cloud credentials, and model API keys from the original secret manager;
- published container image names/digests and Kubernetes manifests.

Never place recovered secrets in Markdown, Git, Dockerfiles, migration seed data, browser storage, or screenshots.

## Preventive backup package

Keep source, state, and secrets as three separate recoverable assets. A pushed Git branch protects source but not PostgreSQL profiles/runs; a database dump protects encrypted values but not the encryption key.

```bash
# 1. Source and documentation
git status
git add AGENTS.md README.md start.sh docs src
git commit -m "checkpoint recoverable Agent Studio"
git push origin HEAD

# 2. Durable database state (run from repository root)
mkdir -p backups
docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T postgres \
  pg_dump -U agent_studio -d agent_studio -Fc > backups/agent-studio.dump

# 3. Record images without exporting credentials
docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml images > backups/container-images.txt
git bundle create backups/agent-studio.bundle --all
```

Store `backups/` outside the laptop in encrypted storage. Do not commit dumps: they can contain encrypted credentials, prompts, user data, and run output. Back up the encryption-key reference in a password manager or secret manager separately. Periodically test restoration on a clean Docker environment.

## Target repository skeleton

Create this structure first:

```text
agent-studio/
├── AGENTS.md
├── README.md
├── start.sh
├── docs/
│   ├── 01-product-spec.md ... 20-sources-and-version-notes.md
│   └── rebuild/
└── src/
    ├── pom.xml
    ├── libs/
    │   ├── core-domain/
    │   └── runtime-api/
    ├── services/
    │   ├── control-plane/
    │   ├── runtime-service/
    │   └── slack-mcp/
    ├── workers/
    │   ├── browser-mcp/
    │   ├── example-worker/
    │   └── sample-catalog-worker/
    ├── ui/studio-web/
    ├── deploy/
    │   ├── compose/
    │   ├── docker/
    │   └── helm/
    ├── contracts/
    └── examples/github-registry/
```

Java packages use `dev.agentstudio`. The root Maven project targets Java 21 and contains `core-domain`, `runtime-api`, `control-plane`, `runtime-service`, `slack-mcp`, and `sample-catalog-worker`. Studio is React plus TypeScript and Vite.

## Non-negotiable reconstruction order

### 1. Foundation

Create the Maven reactor, UI package, Dockerfiles, Compose topology, CI, `.gitignore`, root launcher, health endpoints, and configuration validation. The launcher must require only Docker plus Compose v2 on macOS/Linux and must resolve paths relative to itself.

Checkpoint:

```bash
cd src && mvn test
cd ui/studio-web && npm install && npm run build
cd ../../..
./start.sh
```

Do not advance until an empty stack starts and `http://localhost:8080/` responds.

### 2. Stable domain and invocation contracts

Implement framework-free records/value objects for agents, immutable versions, capabilities, and logical model profiles in `core-domain`. Implement `AgentExecutionRequest`, `ExecutionContext`, `AgentExecutionResult`, and `AgentRuntimeAdapter` in `runtime-api`.

Rules:

- agent versions reference logical model profiles and logical capability IDs;
- they never contain model endpoints, MCP URLs, cloud SDK objects, or framework DTOs;
- every request propagates tenant, user, scopes, run ID, deadlines, and budgets;
- custom code runs out of process;
- never persist hidden chain-of-thought.

### 3. PostgreSQL and control plane

Create the Spring Boot control plane and Flyway migrations in order. PostgreSQL is authoritative. Build tenant-aware APIs for users, agents, versions, model connections/profiles, capabilities/providers/bindings, integrations, deployments, registries, and skills.

Recreate migrations in the sequence listed in `18-contract-and-file-inventory.md`. If restoring an existing database, use the exact historical migrations. If starting a genuinely new product lineage, equivalent consolidated migrations are acceptable only before the first release.

Required invariants:

- versions are immutable and running executions pin one version;
- active version is a separate lifecycle pointer;
- user-owned secrets are encrypted before insert and masked on every response;
- delete APIs remove durable rows or archive records with historical run references;
- every catalog query is tenant-scoped;
- integration form definitions load from `organization_integration_types`.

### 4. Runtime service and run ledger

Create runs and semantic events before asynchronous execution begins. Add list/get/start/cancel/event/SSE APIs. Resolve the active agent version once and pin it. Implement adapters for config-driven, remote, and isolated Docker workers behind `AgentRuntimeAdapter`.

The bounded loop is:

```text
message
  → parse/plan within configured iteration and cost limits
  → resolve logical capabilities to enabled named provider bindings
  → invoke tools and record grounded evidence
  → optionally repeat while useful and within budget
  → synthesize one structured final response
  → persist terminal status and usage
```

Emit semantic events such as run created/started, execution dispatched, planning, tool requested/completed/failed, model requested/completed, usage recorded, response completed, run failed/cancelled. Do not expose private reasoning.

### 5. Model gateway

Implement OpenAI, Anthropic, and OpenAI-compatible connections behind one gateway. Saving a connection requires backend verification for supported providers. Agents select a logical profile; the gateway selects connection/model and records tokens, calls, duration, and estimated cost.

Support mock/local behavior so the stack can be verified without paid credentials. A model call may interpret requests and synthesize answers, but factual data requested through configured capabilities must come from tool evidence.

### 6. Capability gateway and MCP adapters

Implement capability catalog, provider profiles, provider bindings, per-agent profile selection, policy/risk metadata, credential resolution, and auditing. A tool call must resolve:

```text
agent version capability
  → optional per-agent provider selection
  → enabled compatible provider binding
  → endpoint and encrypted credential profile
  → adapter invocation
```

Provide samples for public HTTP extraction, Playwright headless browsing, read-only database queries, Redis memory/search, chart generation, Slack messaging/file upload, and email intent. Public web wildcards may allow any public HTTP(S) host, but DNS/IP validation must block loopback, private, link-local, and metadata networks.

### 7. Memory and response composition

Use Redis for hot conversation state and bounded exact tool-result caching. Use PostgreSQL for durable memory metadata/history. Preserve evidence and chart specifications in a standard response envelope so one assistant message can contain formatted text, tables, multiple charts, images, files, citations, and notices.

Never let cached data bypass tenant isolation, TTL, capability policy, or freshness requirements.

### 8. Registry-driven extensibility

Implement GitHub registry sources with explicit `Sync → Pull → Promote` stages:

- Sync reads bounded `catalog.json` metadata.
- Pull fetches one selected artifact and referenced MCP configuration schema; reject absolute/traversal paths and oversized content.
- Promote materializes an agent draft, available skill, or MCP logical capabilities.
- MCP promotion creates an organization integration type from JSON Schema plus a disabled provider and disabled bindings.
- Configuration, deployment, health verification, and explicit enablement are required before runtime use.

Never dynamically load repository JARs into the control plane or shared runtime. Build and run reviewed custom code only in isolated workers/containers. Add signing, scanning, SBOM, provenance, and approval gates before production auto-build.

### 9. Studio UI

Create the post-login bootstrap call first; it establishes organization/user context and loads server-backed settings. Build pages for Agents, Agent workspace, Runs, Capabilities/integrations, Model profiles, Registries, Deployments, Memory, Observability, and User profile.

UI rules:

- use dropdowns for stored selections;
- reload backend state after create/update/delete/promote;
- derive integration fields from the server catalog;
- use a full workspace/tab for agent chat, not a cramped modal;
- keep execution traces in Runs, grouped by full run ID;
- show one rich final assistant response in chat;
- all dialogs must fit the viewport, scroll internally, and align responsive fields.

### 10. Placement, cleanup, deployment, and orchestration

Support `AUTO`, `IN_PROCESS`, `DOCKER`, and `KUBERNETES` intent. Lightweight agents may run in-process; arbitrary code must not. Docker workers must be created from a known runtime image and removed on completion, cancellation, timeout, or workspace closure. Generate portable Kubernetes YAML without cloud SDK types in core contracts.

Temporal is required before declaring schedules, durable long-running workflows, approvals, retries, or multi-agent execution operational. Workflow code remains deterministic; model/tool/network I/O belongs in activities. Child agents share a root run and budget while retaining child run IDs.

### 11. Security and operations

Add OIDC, tenant membership, RBAC/ABAC, workload identity, secret references, rate/cost/tool budgets, risk approval, audit events, OpenTelemetry, structured logs, health/readiness, backups, migration upgrade tests, and dependency/image scanning.

## Database recovery

For a backup restore, use a new database instance first. Supply the original encryption key to control-plane and runtime-service before verifying profiles. Start PostgreSQL, restore the dump, then start the control plane so Flyway applies only newer migrations. Inspect `flyway_schema_history`; never repair or delete entries casually.

Redis is disposable only if losing hot conversations and cache entries is acceptable. PostgreSQL is not disposable. A normal `./start.sh --stop` must preserve all volumes.

Example restore into an empty local PostgreSQL volume:

```bash
docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml up -d postgres
docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T postgres \
  pg_restore -U agent_studio -d agent_studio --clean --if-exists \
  < backups/agent-studio.dump
./start.sh
```

`--clean` is destructive to the target database. Use it only for an explicitly disposable/empty recovery target, never casually against the only production database.

## Definition of recovered

Recovery is complete only when all checks in `12-verification-checklist.md` pass, a fresh clone starts with `./start.sh`, secrets remain masked, one tool-grounded run succeeds with correlated events, Docker cleanup works, and known incomplete features remain honestly labelled.
