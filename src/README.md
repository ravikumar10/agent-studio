# Enterprise Agent Studio — implementation

This directory contains the Docker-runnable implementation derived from the specification in the repository root. It delivers the control-plane registry, immutable agent versions, runtime contracts, run lifecycle, semantic events, all V1 runtime routing modes, an isolated example worker, and the Studio UI without introducing framework dependencies into `core-domain`.

## Layout

- `libs/core-domain`: pure Java 21 domain records, invariants, and lifecycle rules.
- `services/control-plane`: Spring Boot tenant-aware agent registry and Flyway migrations.
- `services/runtime-service`: run/version resolution, execution, cancellation, and semantic events.
- `libs/runtime-api`: framework-neutral worker invocation contract.
- `workers/example-worker`: isolated JVM worker proving out-of-process invocation.
- `workers/sample-catalog-worker`: Docker-deployable website/database sample agents and their
  least-privilege tool services.
- `ui/studio-web`: React + TypeScript Studio shell.
- `deploy`: local PostgreSQL Compose, container builds, and a portable Helm skeleton.

## Run with Docker

Docker is the only required local dependency:

```bash
cd src
docker compose -f deploy/compose/compose.yml up -d --build
```

Open `http://localhost:8080`. Nginx serves the Studio and routes control-plane and runtime APIs through the same port. PostgreSQL is exposed on `localhost:5432` for development.

The Compose stack also starts two ready-to-run sample agents. They are seeded for the
`local-development` tenant and pinned to version `1.0.0`:

```bash
curl -X POST http://localhost:8080/api/v1/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: local-development' \
  -d '{"agentId":"database-reader","input":{"category":"electronics"},"subjectId":"sample-user","scopes":["agents:invoke"],"async":false}'

curl -X POST http://localhost:8080/api/v1/runs \
  -H 'Content-Type: application/json' \
  -H 'X-Tenant-Id: local-development' \
  -d '{"agentId":"website-reader","input":{"url":"https://example.com"},"subjectId":"sample-user","scopes":["agents:invoke"],"async":false}'
```

`database-reader` uses a dedicated seeded PostgreSQL container and exposes only an approved,
parameterized SELECT operation. `website-reader` accepts HTTPS URLs only, enforces an exact hostname
allow-list, blocks local/private addresses, disables redirects, and bounds returned content. The agent
worker and both tool services are separate non-root Java 21/Spring Boot containers.

The Runs screen receives state and semantic-event changes over Server-Sent Events rather than polling.
The development stream is available at
`GET /api/v1/runs/stream?tenantId=local-development`; it emits named `run`, `run-event`, and `heartbeat`
events. Nginx buffering is disabled for this route, the browser reconnects automatically, and the UI
shows the current stream connection state.

Stop the stack without deleting its database:

```bash
docker compose -f deploy/compose/compose.yml down
```

## Run without Docker

Prerequisites: Java 21, Maven 3.9+, Node 22+, npm, and Docker.

```bash
cd src
docker compose -f deploy/compose/compose.yml up -d
mvn verify
mvn -pl services/control-plane -am spring-boot:run
```

In a second terminal:

```bash
cd src/ui/studio-web
npm install
npm run dev
```

All public API requests must include `X-Tenant-Id`. Example:

```bash
curl -X POST http://localhost:8080/api/v1/agents \
  -H 'Content-Type: application/json' -H 'X-Tenant-Id: demo' \
  -d '{"id":"shipment-investigator","displayName":"Shipment Investigator","ownerTeam":"logistics-ai","tags":["shipment"]}'
```

The Phase 1 catalog endpoints are `GET/POST /api/v1/agents`, `GET/POST /api/v1/capabilities`,
and `GET/POST /api/v1/model-profiles`. Agent versions are created under
`/api/v1/agents/{agentId}/versions` and moved through explicit lifecycle action endpoints.

## Git repository registries

The sample portable registry is under `examples/github-registry` and is ready to publish as
`ravikumar10/agent-studio-sample-registry`. A repository exposes a root `catalog.json`; agents bind
logical capabilities, MCP manifests describe deployment requirements without credentials, and skills
contain progressively loaded instructions.

Register the same repository separately for `AGENT`, `MCP`, and `SKILL`, then use:

- `POST /api/v1/registries/{registryId}/sync` to discover matching catalog entries.
- `GET /api/v1/registries/{registryId}/artifacts` to review discovered entries.
- `POST /api/v1/registries/{registryId}/artifacts/{artifactId}/pull` to explicitly cache approved content.

Only public GitHub HTTPS repository URLs are accepted in this first adapter. Repository content is
bounded to 1 MB per artifact, paths are traversal-checked, and sync never activates or executes content.

## Runtime coverage

- `CONFIG` executes in the platform harness using the deterministic local adapter.
- `EMBABEL` routes through the stable internal contract to an isolated worker container; install the real Embabel runtime only in that worker boundary.
- `REMOTE_HTTP` and `REMOTE_A2A` route out of process without leaking remote DTOs into platform contracts.

The Docker profile intentionally uses local/mock implementations and development credentials. Production OIDC, real model providers, MCP registrations, Temporal durability, TLS, and secret-provider configuration remain deployment integrations and must be supplied before production use.
