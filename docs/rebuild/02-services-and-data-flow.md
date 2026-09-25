# Services and data flow

## Local topology

- `studio-web`: React bundle plus Nginx reverse proxy on host port 8080.
- `control-plane`: catalogs, profiles, registries, deployments, users, agent versions, and Flyway.
- `runtime-service`: run creation, version resolution, placement, agent adapters, tool/model calls, events, SSE, cancellation, and observability.
- `postgres`: durable system of record.
- `redis`: hot memory and bounded exact-result cache.
- `sample-agent-worker`: out-of-process sample/code agent contract.
- `web-reader-tool`: governed HTTP/web extraction adapter.
- `browser-mcp`: Playwright-based rendered-page navigation/extraction/actions.
- `database-reader-tool`: read-only sample database adapter.
- `sample-data`: isolated demonstration PostgreSQL database.
- `docker-api-proxy`: restricted Docker socket surface used by runtime-service.

## Invocation flow

1. Studio posts `StartRunRequest` to `/api/v1/runs` with tenant/user headers.
2. Runtime resolves the requested or active immutable `AgentVersion`.
3. A run row and `run.created` event are committed before execution.
4. Placement resolves to in-process, Docker, or Kubernetes intent.
5. The runtime adapter performs bounded planning.
6. Logical capabilities resolve through per-agent provider bindings.
7. Tool outputs become grounded evidence; the model synthesizes the final response.
8. Semantic tool/model/usage events are stored with the same `run_id`.
9. Run output/status is persisted and broadcast over SSE.
10. Studio shows the final response in chat and the complete trace in Runs.

## Dependency rules

Core domain never imports provider, framework, cloud, A2A, or MCP transport DTOs. Runtime adapters depend on the stable runtime API. Tool endpoints are resolved from logical capabilities, never embedded in agent definitions. Model endpoints are resolved from logical profiles, never stored in prompts or AgentVersion contracts.
