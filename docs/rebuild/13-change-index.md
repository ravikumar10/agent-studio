# Change index: feature to implementation

Use this index to locate the code that implements each major increment. Paths are relative to `src/` unless noted.

| Change | Primary implementation | Durable migration/config | Verification focus |
|---|---|---|---|
| Core agent registry and immutable versions | `services/control-plane`, `libs/core-domain` | early control-plane Flyway migrations | version lifecycle and active pointer |
| Sample website/database agents | `workers/sample-catalog-worker` | `V4__sample_agents.sql` | remote invocation and pinned versions |
| Run execution and SSE | `services/runtime-service/RunService.java`, `RunStore.java`, `RunEventStream.java` | run/event tables | event ordering and reconnect |
| Database MCPs and skills | database reader worker, capability/provider controllers | `V7__database_mcp_and_skills.sql` | read-only enforcement and profile binding |
| Agent Creator | `runtime-service/AgentCreatorService.java` | `V8__agent_creator.sql` | creates valid immutable versions |
| User profiles and encrypted config | model/integration/profile controllers | `V9__user_profiles_and_encrypted_configuration.sql` onward | masking, ownership, deletion |
| Deployment environments and Brain | `control-plane/DeploymentController.java` | `V12__deployment_environments_and_brain.sql` | stored portable plans, not false apply claims |
| Capability providers and routing | `CapabilityProviderController.java`, runtime provider resolver | `V14__capability_providers.sql` | logical ID to named profile resolution |
| Agent runtime placement/config | `AgentRuntimeConfigurationController.java`, `ExecutionPlacementService.java` | `V16__agent_runtime_configuration.sql` | placement, trigger validation, selected profile |
| Typed integrations | `IntegrationTypeCatalog.java` | `V17__typed_integration_profiles.sql`, `V20__organization_integration_catalog.sql` | organization-scoped form schemas |
| Organization bootstrap | `StudioBootstrapController.java` | `V18__organization_runtime_bootstrap.sql` | reload after login from backend |
| HTTP and browser MCP | `WebReaderToolController.java`, `workers/browser-mcp/server.mjs` | `V19__http_and_browser_mcp_capabilities.sql` | public web allowed; SSRF blocked |
| Knowledge and charts | runtime capability pipeline/response composer | `V22__content_and_chart_mcp_capabilities.sql` | tool-grounded multi-chart response |
| Provider binding repair | runtime/control provider resolution | `V23__repair_web_capability_bindings.sql` | newly composed web agents run without manual URL wiring |
| Email capability | provider/integration catalog | `V24__email_mcp_capabilities.sql` | approval and recipient controls |
| Open public web defaults | Compose, HTTP worker, browser MCP, integration schema | `V25__allow_public_web_hosts.sql` | wildcard public domain success plus private-IP rejection |
| Chat-focused workspace | `ui/studio-web/src/PlaygroundPanel.tsx` | none | final rich answer only; cancellation on close |
| Run-correlated trace UI | `ui/studio-web/src/main.tsx`, `builder.css` | existing run events | all calls grouped by full run ID |
| Observability windows/cost/calls | runtime `ObservabilityController.java`, UI observability page | run events | 1m through 30d filters |
| Docker worker cleanup | `runtime-service/IsolatedWorkerClient.java` | runtime configuration | terminal and cancelled containers removed |
| One-command local operation | root `start.sh`, Compose/Dockerfiles | Compose environment and volumes | clean Docker host startup |

## Files to inspect before modifying a feature

Always inspect the current controller/service, the relevant Flyway migrations, React API consumer, Compose environment, tests, and the corresponding specification document. Search by logical capability or table name with `rg`; do not assume filenames remain unchanged.

## Historical seed data versus desired architecture

Migrations intentionally reconstruct historical bootstrap data. Future work should make registry publication and integration schemas more dynamic without rewriting those migrations. Add new migrations and adapters while maintaining upgrade compatibility.
