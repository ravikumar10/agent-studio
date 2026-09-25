# 17 — Non-Functional Requirements

Initial targets are measurable/configurable, not hard-coded limits.

- Availability: control/runtime target 99.9%+ excluding upstream providers.
- Horizontal scale for stateless services.
- Design for thousands of agents, tens of thousands of capabilities/providers and hundreds of concurrent runs per cluster.
- Durable run state survives worker/node restart.
- Tenant isolation and least privilege mandatory.
- No mandatory cloud-managed service.
- Zero-downtime rollout where feasible; backward-compatible DB migrations.
- Every model call records usage/cost where available; budgets enforced independently of prompts.
- Every deployable exposes liveness, readiness, metrics and build/version info.
