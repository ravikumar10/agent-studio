# 04 — Domain Model

## AgentDefinition
Mutable metadata: `id`, `displayName`, `description`, `ownerTeam`, `tags`, `status`, audit fields. Not executable.

## AgentVersion
Immutable after validation: `agentId`, semver `version`, `runtimeType`, `hostingMode`, `artifactRef|remoteEndpointRef`, `capabilitiesProvided[]`, `toolCapabilitiesRequired[]`, `agentCapabilitiesRequired[]`, `modelProfile`, `promptRef`, input/output schema refs, memory policy, execution/security policy refs, checksum and lifecycle.

Lifecycle: `CREATED -> VALIDATED -> CANARY -> ACTIVE -> RETIRED`, plus `REVOKED`.

## Capability
`capabilityId`, `displayName`, description, `kind=TOOL|AGENT|MODEL_SERVICE`, input/output schemas, risk class, owner, tags.

## CapabilityProvider
Concrete mapping: `providerId`, `capabilityId`, `providerType=MCP|REST|GRPC|EVENT|DB|AGENT`, endpointRef, remoteOperationName, provider version, environment, auth profile, routing weight, health, enabled.

## ModelProfile
Logical policy: id, quality/latency tier, token limits, required features, allowed provider models, fallback/cost policy.

## Run
`runId`, tenant/subject, agent/version, parentRunId, status, timestamps, deadline, budget, traceId. Status: CREATED/RUNNING/WAITING/WAITING_APPROVAL/COMPLETED/FAILED/CANCELLED/TIMED_OUT.

## Task
Delegated work with task/parent/run IDs, requested capability, assigned agent/version, status and artifact refs.

## ToolInvocation
Invocation IDs, capability/provider, risk class, authorization decision, hashes/artifact refs, status, latency.

## Artifact
Tenant, media type, storageRef, checksum, classification, retention.

## Immutability rule
Any validated change to prompt, tools, model profile, permissions, schema, runtime or artifact produces a new AgentVersion.
