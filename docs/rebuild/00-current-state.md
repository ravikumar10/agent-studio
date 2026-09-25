# Current implementation state

## Implemented vertical slices

- Java 21/Spring Boot control plane and runtime service.
- React/TypeScript Studio served by Nginx.
- PostgreSQL registry/configuration/run storage with Flyway migrations.
- Redis for hot memory and exact read-only tool-result caching.
- Tenant- and user-scoped agent, model, integration, deployment, and registry configuration.
- Immutable agent versions with active-version resolution and pinned runs.
- OpenAI, Anthropic, and OpenAI-compatible model connection profiles with encrypted credentials and backend connection tests.
- Logical capabilities, provider profiles, provider bindings, and per-agent provider selection.
- Bounded config-agent loop: plan, invoke MCP-backed tools, synthesize grounded response, emit semantic events.
- HTTP reader, headless browser, database reader, Redis/memory, chart, and email capability definitions.
- Public HTTP(S) web access by default with localhost/private/link-local/metadata targets blocked.
- Chat/task workspace with rich Markdown, tables, charts, images, code, files, and notices.
- Per-run event storage, SSE updates, cancellation, Docker worker cleanup, Runs trace UI, and aggregate observability.
- Local Docker Compose topology and one-command `start.sh`.
- Repository registry discovery, bounded artifact download, and explicit promotion for AGENT, MCP, and SKILL artifacts.
- Organization-scoped integration types generated from promoted MCP JSON configuration schemas.
- Portable Kubernetes manifest generation and stored deployment plans.

## Partially implemented

- Integration forms are database-backed at runtime, but their default schemas are still bootstrapped from Java definitions.
- Promotion validates the structural manifest and materializes catalog records, but cryptographic artifact signing, vulnerability scanning, build provenance, and a production approval workflow remain future hardening.
- Kubernetes plans can be generated and stored; applying to a real cluster requires a deployment adapter/credential implementation.
- Redis semantic/vector concepts exist, but production embedding/index management is not complete.
- Email is represented as a governed MCP capability; a production server and delivery approval workflow must be configured.

## Not yet operational

- Durable Temporal orchestration.
- Automatic execution of scheduled agent configurations.
- Event-driven/webhook trigger consumers.
- Multi-agent delegation and aggregation despite topology/member configuration being stored.
- Parent/child run graphs and shared root-run budgets.
- Production OIDC/RBAC/ABAC and full OpenTelemetry export.
- Embabel and A2A production adapters.

## Never claim these without tests

- A scheduled selector in the UI does not mean a scheduler is firing runs.
- A `MULTI_AGENT` topology does not mean member agents are invoked.
- A pulled registry artifact is not executable until explicitly promoted, configured, deployed, verified, and enabled.
- A generated Kubernetes YAML document is not proof that it was applied.
- A model profile saved without successful backend validation is not usable.

## Definition of a working feature

A feature is complete only when configuration persists, runtime consumes it, semantic events are recorded under a run ID, cancellation/cleanup work, the UI reflects backend state after reload, tests cover the contract, and the Docker stack demonstrates the path.
