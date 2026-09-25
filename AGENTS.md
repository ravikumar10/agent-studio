# AGENTS.md — Mandatory Instructions for Codex

Treat this repository as a **specification source of truth**. Do not silently change architecture.

## Non-negotiable rules
1. Java 21 is the primary backend language.
2. Use Spring Boot for backend deployable services.
3. Use React + TypeScript for the Studio UI.
4. Use Temporal for durable, long-running orchestration.
5. Use Spring AI MCP / official MCP Java SDK in the Tool Gateway.
6. Embabel is a preferred/default JVM agent runtime **adapter**, never the platform core.
7. Python/LangGraph/CrewAI/custom runtimes execute out-of-process behind the same invocation contract.
8. A2A v1.0 interoperability is an adapter; internal domain contracts never depend on A2A DTOs.
9. Agents bind logical capabilities, never raw URLs or cloud SDKs.
10. Model endpoints are selected by Model Gateway from logical model profiles.
11. Platform must work on AKS, EKS, GKE, OpenShift, generic Kubernetes and on-prem Kubernetes.
12. No cloud-specific SDK types in core-domain/runtime-api/registry contracts.
13. Never dynamically load arbitrary developer JARs into Control Plane or shared Harness JVM.
14. Custom code agents run in isolated workers/containers.
15. Running executions pin immutable agent versions; new runs resolve the active version.
16. Tenant/user/scopes propagate through agent, tool and remote-agent invocation.
17. Side-effect tools carry risk metadata and pass policy checks.
18. External I/O never executes directly in deterministic Temporal Workflow code.
19. Do not expose/store hidden chain-of-thought; emit semantic execution events only.
20. All framework/provider integration belongs behind adapters.

## Dependency direction
Allowed: `control-plane -> core-domain`; `runtime-service -> runtime-api`; `runtime-embabel -> runtime-api + Embabel`; `tool-gateway -> capability-api + Spring AI MCP`; `temporal-worker -> runtime-api`.

Forbidden: `core-domain -> Embabel/LangGraph/A2A/cloud SDK`; `agent definition -> MCP URL`; `agent definition -> provider model endpoint`.

## Quality bar
Every production module needs unit tests, contract/integration tests where applicable, health/readiness, OpenTelemetry, structured logs, configuration validation, and compatibility-conscious APIs.

## Implementation order
Follow `docs/18-implementation-plan.md` phase-by-phase.
