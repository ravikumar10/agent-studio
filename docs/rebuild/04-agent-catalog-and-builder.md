# Agent catalog, builder, and registries

## Agent definition versus version

`AgentDefinition` is mutable catalog metadata: ID, display name, description, owner, tags, interaction mode, topology, trigger mode, and status. `AgentVersion` is immutable executable configuration: runtime/hosting adapters, artifact reference, provided and required capabilities, model profile, skills/prompt reference, schemas, policies, checksum, and lifecycle.

New executions resolve the active version; existing executions remain pinned. Updating configuration creates a new semantic version rather than changing a running version.

## UI creation modes

- Compose: builds a CONFIG agent from model, ordered skills, logical MCP capabilities, memory, policy, placement, and trigger settings.
- Import code: registers an artifact reference and requires isolated/out-of-process execution.
- Agent Creator: accepts a chat description and produces a governed draft/version using registered models and capabilities.

The natural-language description is metadata and design input. It is not implicitly the runtime input for scheduled jobs. Scheduled input must be explicit.

## Registry model

Registry types are AGENT, MCP, and SKILL. A GitHub registry exposes `catalog.json`; sync stages artifact metadata and pull stores immutable content. Required production flow is discover → pull → validate → approve → publish. Publication materializes an artifact into the appropriate organization catalog. Never execute mutable repository content directly during a run.

## Current limitation

Discovery and pull are implemented; validation/approval/materialization is not. Rebuild this before describing registries as fully dynamic. Default integration form schemas should ultimately come from an approved bootstrap registry or database templates rather than Java constants.
