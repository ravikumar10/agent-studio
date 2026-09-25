# 02 — Architecture

```mermaid
flowchart TB
 UI[Agent Studio UI] --> CP[Control Plane]
 CP --> AR[Agent Registry]
 CP --> CR[Capability Registry]
 CP --> MR[Model Profile Registry]
 CP --> PR[Prompt/Config Registry]
 CP --> ER[Eval & Release Registry]
 CP --> POL[Policy Registry]
 CP --> RT[Agent Runtime / Harness]
 RT --> RES[Agent Resolver]
 RT --> CTX[Context Compiler]
 RT --> MEM[Memory Manager]
 RT --> PE[Policy & Budget Engine]
 RT --> MG[Model Gateway]
 RT --> TG[Tool Gateway]
 RT --> AG[Agent Gateway]
 TG --> MCP[MCP Servers]
 TG --> API[REST/gRPC/Event/DB adapters]
 AG --> EW[Platform/Client Workers]
 AG --> A2A[Remote A2A Agents]
 RT --> TW[Temporal Worker]
 TW --> TS[Temporal Service]
```

## Planes
**Control Plane:** configuration, discovery, lifecycle and governance; no agent business-workload execution.  
**Execution Plane:** Harness, gateways, workers and runtime adapters.  
**Capability/Data Plane:** MCP servers, APIs, DB/search/event adapters and enterprise systems.  
**Durable Orchestration Plane:** Temporal service/workers.

## Key abstractions
- `AgentDefinition`: mutable logical agent draft.
- `AgentVersion`: immutable executable version.
- `AgentRuntimeAdapter`: executes an AgentVersion.
- `Capability`: logical callable business capability.
- `CapabilityProvider`: concrete MCP/API/agent implementation.
- `ModelProfile`: logical model requirements/policy.
- `ExecutionContext`: tenant/subject/scopes/run/deadline/budget/classification.
- `Run`, `Task`, `Artifact`, `ToolInvocation`.

## Invocation rule
All governed cross-boundary calls use gateways: Agent -> Model Gateway; Agent -> Tool Gateway; Agent -> Agent Gateway. Agents do not select arbitrary network addresses.

## Framework neutrality
Embabel code exists only in `runtime-embabel`. Python frameworks use the internal invocation protocol. A2A exists only at the adapter edge.

## Portability
Use Kubernetes, PostgreSQL, Redis-compatible cache, object-storage abstraction, OIDC/OAuth2 and OpenTelemetry. Provider-specific features are adapters.
