# 19 — Architecture Decisions

- **ADR-001 Java-first platform:** Java 21/Spring Boot; Python out-of-process.
- **ADR-002 Embabel adapter:** preferred JVM agent runtime but never core dependency.
- **ADR-003 MCP for tools:** current Spring AI MCP/official Java SDK in Tool Gateway.
- **ADR-004 A2A adapter:** A2A v1.0 for remote interoperability; internal contract canonical.
- **ADR-005 Temporal durability:** Temporal surrounds bounded agent execution.
- **ADR-006 Capability-first:** agents bind logical capabilities, not endpoints.
- **ADR-007 Run anywhere:** platform/client/external hosting are first-class.
- **ADR-008 Immutable AgentVersion:** required for reproducibility/rollback.
- **ADR-009 No arbitrary in-process plugins:** custom code isolated in workers.
- **ADR-010 Kubernetes baseline:** cloud/private/on-prem portability.
- **ADR-011 Semantic events only:** no hidden chain-of-thought exposure.
- **ADR-012 Protocol independence:** central Tool Gateway tracks MCP independently of Embabel/framework protocol lag.
- **ADR-013 Deployment plans before apply:** deployment environments, immutable-version plans, generated manifests and schedules are tenant-scoped control-plane data. Cloud fields live in adapter configuration; credentials remain SecretProvider references. Kubernetes mutation is a side-effect capability handed to a durable DeploymentWorkflow adapter, never executed directly by control-plane domain code.
- **ADR-014 Bounded orchestration Brain:** the Brain is a stored, observable orchestration policy (deterministic-first planning, budgets, approvals, health gates and rollback), not a privileged autonomous runtime and never a store for hidden chain-of-thought.
