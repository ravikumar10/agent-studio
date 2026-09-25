# 14 — Release and Versioning

Agent versions: CREATED -> VALIDATED -> CANARY -> ACTIVE -> RETIRED; REVOKED is exceptional. Content is immutable after validation.

## Gates
Schema/runtime/artifact validation; tool permission diff; model profile diff; prompt/config diff; security policy; eval threshold; latency/cost threshold; smoke test.

## Canary
Routes configurable subset of **new runs** only. Existing runs stay pinned.

## Rollback
Changes active pointer for new runs. Never silently mutate/switch in-flight agent version. Emergency revocation is explicit policy.

## Provider evolution
Capability provider implementations evolve independently, but validated AgentVersion maintains compatible/pinned binding semantics.

## Environment promotion
Promote the same immutable AgentVersion dev -> test -> prod. Environment-specific provider bindings can differ while capability semantics remain compatible.
