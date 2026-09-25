# Database and migration reconstruction

PostgreSQL is authoritative. UI state must reload from backend APIs after login; browser-local state is never the system of record.

## Principal tables

- `organization_accounts`, `user_profiles`, organization membership/settings.
- `agents`, `agent_versions`, `agent_release_state`.
- `agent_runtime_configurations`, `agent_capability_profile_bindings`.
- `capabilities`, `capability_providers`, `capability_provider_bindings`, provider secrets.
- `model_connections`, `model_profiles` and encrypted credential storage.
- `registries`, `registry_artifacts`, `available_skills`.
- `runs`, `run_events`.
- `memory_records` plus Redis keys for hot state/cache.
- `deployment_environments`, `deployment_plans`, `agent_schedules`, `brain_profiles`.
- `organization_integration_types` for runtime form definitions.

## Migration discipline

- Every schema or bootstrap-data change is a new ordered Flyway migration.
- Never modify a migration already released/applied.
- Make migrations idempotent where bootstrap data may already exist using appropriate conflict handling.
- Preserve tenant IDs in every compound primary/foreign key.
- Store configurable schemas/specifications as JSONB, but keep identifiers, lifecycle state, ownership, and timestamps as typed columns.
- Secrets are encrypted before persistence and never returned unmasked.
- Immutable AgentVersion rows referenced by runs must not be deleted. Archive the agent definition when history exists.

## Rebuild test

Test both an empty database and an upgraded existing volume. Confirm Flyway history success, bootstrap tenant availability, API reads after restart, and deletion behavior with historical runs.
