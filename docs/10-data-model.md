# 10 — Data Model

Primary relational store: PostgreSQL.

Tables include `agents`, `agent_versions`, `agent_release_state`, `capabilities`, `capability_providers`, `mcp_servers`, `model_profiles`, `model_provider_bindings`, `runs`, `run_events`, `agent_sessions`, `session_turns`, `session_evidence`, `agent_public_exposures`, `tasks`, `tool_invocations`, `approvals`, `artifacts`, `eval_suites`, `eval_cases`, `eval_runs`, `eval_case_results`, and `client_runtimes`.

## Storage rules
Relational metadata/state -> PostgreSQL.  
Durable conversation/session ownership, bounded turns, and grounded evidence -> PostgreSQL.
Transient session projection and cache acceleration -> Redis.

Tool-result reuse uses tenant-isolated SHA-256 fingerprints in Redis with a short TTL. It is fail-open and is limited to governed read-only tool results; Redis is never the durable source of truth.

Session context uses a tenant/agent/subject-bound `agent_sessions` row, ordered sanitized `session_turns`, and content-hashed `session_evidence`. Redis may cache the bounded projection, but a cache miss or outage must rehydrate from PostgreSQL. Public exposure records pin an immutable agent version and store only opaque identifiers, origin policy, and API-key verifier hashes—not plaintext keys.

Semantic knowledge retrieval is a separate optional adapter. A Redis deployment with Search/Vector support may store embeddings and perform bounded similarity queries, but it must record the embedding profile, dimensions, distance metric, source document reference, tenant, classification and expiry. Similarity matches must never replace exact tool-result cache keys because an approximately similar database request is not necessarily equivalent.
Large artifacts -> object store abstraction.  
Vector/search -> pluggable backend.  
Secrets -> secret references only.

## Tenant isolation
All tenant-owned records include tenant_id; enforce in service/repository layer and optionally PostgreSQL RLS.

## Run events
Append-only semantic events with configurable retention.

## Sensitive payloads
Do not persist full tool/model payloads by default. Store hashes/metadata/artifact references according to policy.
