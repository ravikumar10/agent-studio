# Memory, knowledge, and repeated-call suppression

## Tiers

- Hot memory: Redis, short TTL, working/session state.
- Cold memory: PostgreSQL, durable governed records.
- Exact tool cache: Redis SHA-256 fingerprint over tenant, capability, and normalized input; only eligible read-only tools.
- Semantic knowledge: optional Redis Stack vector index plus embeddings; separate from exact cache.

Every key/index namespace includes tenant and agent identity. Chat continuity may store selected grounded content, but must not store hidden reasoning. Secrets and raw credentials are never memory content.

## Retrieval policy

Use exact cached tool output when the same bounded read request repeats. Use semantic retrieval only when similarity is useful. Attach provenance and expiration metadata. A cached result must still satisfy the current provider, policy, tenant, and freshness constraints.

## Current state

Durable agent sessions are operational:

- PostgreSQL is the source of truth in `agent_sessions`, `session_turns`, and `session_evidence`.
- Redis stores a disposable projection under `session-context:{tenant}:{session}`. A cache miss or Redis outage falls back to PostgreSQL.
- Every run is pinned to a session, agent, immutable agent version, tenant, and subject. A session cannot be reused by another agent or subject.
- Before execution, the runtime hydrates bounded prior turns and grounded tool evidence. The model planner may reuse sufficient evidence instead of repeating a tool call.
- User, assistant, error, and tool-result records are sanitized; hidden reasoning and credentials are not persisted.
- Tool evidence is content-hashed and deduplicated. Evidence produced before a failed final response is still retained for debugging and safe reuse.
- Semantic events `session.context.loaded` and `session.turn.persisted` expose memory behavior without exposing chain-of-thought.

Runtime tuning is environment driven:

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SESSION_HOT_TTL` | `PT24H` | Redis projection lifetime (ISO-8601 duration) |
| `SESSION_MAX_TURNS` | `20` | Prior turns loaded into a run |
| `SESSION_MAX_EVIDENCE` | `8` | Recent grounded evidence records loaded into a run |

Migration `V36__durable_agent_sessions.sql` creates the durable schema and backfills existing run sessions as legacy-owned sessions. The first authenticated subject to resume a legacy session claims it.

The client-supplied conversation is no longer authoritative. The server reconstructs context from the requested `sessionId`, and PostgreSQL can rebuild the Redis projection at any time.

Vector concepts and capability definitions exist; production embeddings, vector-index lifecycle, relevance evaluation, summarization/compaction, and configurable retention policies remain to be completed.
