# 01 — Product Specification

## Goal
Provide a generic Enterprise Agent Studio allowing teams to create/register agents, attach approved MCP tools/APIs/other agents, select governed model profiles, test/evaluate/release/version agents, run them centrally or on client infrastructure, and centrally observe/govern production execution.

## Product boundaries
### Studio / Control Plane owns
Agent metadata/version registry, capability registry, MCP/API registry, model profiles, prompt/config registry, policies, evaluation definitions/results, release lifecycle, client-runtime inventory and run visibility.

### Harness owns
Run creation, agent resolution/version pinning, context construction, memory access, policy/budget enforcement, model/tool/agent invocation, output validation and semantic execution events.

### Temporal owns
Durable state, retries, timers, waits/signals, HITL suspension/resume, recovery, cancellation and compensation orchestration.

### Agent frameworks own
Bounded reasoning/planning/action selection inside an agent invocation.

## Personas
Agent Developer, Platform Admin, Security Admin, Operator/SRE, Business User/Approver.

## Hosting modes
- `PLATFORM`: platform-managed runtime.
- `CLIENT`: customer/client-hosted runtime registered to central Studio.
- `EXTERNAL`: independently deployed remote agent.

## Runtime types — V1
- `CONFIG`
- `EMBABEL`
- `REMOTE_HTTP`
- `REMOTE_A2A`

Later: `LANGGRAPH`, `CREWAI`, `CUSTOM_GRPC`.

## Core flows
**Agent onboarding:** Draft -> capabilities/model/policy -> playground -> eval -> immutable version -> canary -> active.  
**Runtime:** request -> run -> resolve/pin version -> authorize -> context -> invoke -> tool/model/delegation -> verify/approval -> persist -> result.  
**Capability onboarding:** register MCP/API -> discover/import -> normalize -> risk classify -> approve -> publish/version.  
**Client runtime onboarding:** install edge chart -> establish trust -> register/heartbeat -> map capabilities -> deploy/register agents -> activate.

## V1 non-goals
Model training/fine-tuning, custom inference engine, arbitrary in-process plugins, full drag/drop workflow designer, unrestricted chain-of-thought storage, replacement of domain authorization.

## Value
Reuse, standardization, centralized governance with distributed execution, data-residency support, framework/model/cloud independence, consistent security/cost/observability.
