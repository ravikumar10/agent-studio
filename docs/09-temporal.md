# 09 — Temporal Specification

Temporal is the durable business-process runtime; agent frameworks are bounded reasoning runtimes.

## Determinism
Never perform LLM, MCP, REST/gRPC, DB, file/network or remote-agent I/O directly inside Workflow code. Use Activities.

## Core workflows
**AgentRunWorkflow:** pin agent version, invoke runtime activity, wait for approval, execute side effects, cancellation/compensation, final state.  
**EvaluationWorkflow:** run eval datasets with controlled parallelism.  
**DeploymentWorkflow:** optional later for deploy/canary/promote/rollback.

## Activities
ResolveAgent, BuildContext, InvokeAgent, RequestApproval, PersistArtifact, PublishEvent, side-effect/compensation activities as needed.

Do not model every internal LLM/tool turn as a Temporal workflow step in V1; a bounded agent invocation can loop inside an Activity.

## Versioning
Agent version is immutable application data pinned per run. Temporal workflow-code versioning is separate and follows Temporal-safe deployment/versioning. Never conflate them.

## Retry
Policy depends on operation. Side effects require idempotency before retry.

## Approval
WAITING_APPROVAL via Temporal signal/update with approver, decision, timestamp, action summary and policy-decision reference.

Support Temporal Cloud and self-hosted Temporal via configuration.
