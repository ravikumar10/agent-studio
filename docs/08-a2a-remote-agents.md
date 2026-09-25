# 08 — A2A and Remote Agents

MCP is for tools/resources. A2A is for independent agent-to-agent interoperability. The internal Agent Invocation API remains canonical.

## A2A baseline
Target A2A v1.0 semantics: Agent Card discovery, skills, tasks/messages/artifacts, streaming where supported, and supported JSON-RPC/gRPC/HTTP+JSON bindings.

## Registration
Store agent/version, Agent Card URL or imported snapshot, supported interfaces, selected binding, skills mapped to internal capability IDs, auth profile, health, owner and trust level.

## Adapter
Translate internal AgentExecutionRequest/Result to/from A2A task/message/artifact representations. No A2A DTO outside adapter packages.

## Security
TLS, workload/service authentication, approved delegated token exchange only, Agent Card/endpoints allowlisted/validated, egress policy and invocation audit.

## Embabel
Embabel can expose A2A, but platform interoperability never relies on framework-specific behavior.

## Failure semantics
Use idempotency for task creation; correlate async tasks; propagate cancellation when supported; retry only idempotent stages.
