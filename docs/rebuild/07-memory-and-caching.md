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

Redis hot memory and exact caching are operational. Vector concepts and capability definitions exist; production embeddings, index lifecycle, relevance evaluation, and retention controls remain to be completed.
