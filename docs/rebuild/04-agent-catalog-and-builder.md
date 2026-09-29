# Agent catalog, builder, and registries

## Agent definition versus version

`AgentDefinition` is mutable catalog metadata: ID, display name, description, owner, tags, interaction mode, topology, trigger mode, and status. `AgentVersion` is immutable executable configuration: runtime/hosting adapters, artifact reference, provided and required capabilities, model profile, skills/prompt reference, schemas, policies, checksum, and lifecycle.

New executions resolve the active version; existing executions remain pinned. Updating configuration creates a new semantic version rather than changing a running version.

## UI creation modes

- Compose: builds a CONFIG agent from model, ordered skills, logical MCP capabilities, memory, policy, placement, and trigger settings.
- Import code: registers an artifact reference and requires isolated/out-of-process execution.
- Agent Creator: accepts a chat description and produces a governed draft/version using registered models and capabilities.

The natural-language description is metadata and design input. It is not implicitly the runtime input for scheduled jobs. Scheduled input must be explicit.

The builder also stores a fixed `initialPrompt` inside the versioned `plan://` prompt reference. It defines role, objective, grounding, and response expectations for every run. Tool selection is bounded to the immutable version's ordered logical capabilities; chat text may influence which attached tool the model chooses, but it cannot add a new tool or rewrite the configuration.

## Publishing an agent

Each version may be configured for a trigger API, widget, or both. Saving publication settings creates an opaque stable public ID for the logical agent and points it at the newly saved version. Endpoint-only publication may be `PUBLIC` or `API_KEY`; widgets are public because embedding a browser secret would not protect it. Allowed origins drive CORS and widget `frame-ancestors`. See `../35-agent-endpoints-and-widgets.md` for request examples and limitations.

## Registry model

Registry types are AGENT, MCP, and SKILL. A GitHub registry exposes `catalog.json`; sync stages artifact metadata and pull stores immutable content. Required production flow is discover → pull → validate → approve → publish. Publication materializes an artifact into the appropriate organization catalog. Never execute mutable repository content directly during a run.

## Current registry behavior

Discovery, bounded pull, and explicit promotion are implemented. Promotion imports agents as drafts, skills into the skill catalog, and MCP tools as logical capabilities with a schema-driven organization integration type plus disabled provider/bindings. Configuration, isolated deployment, health verification, and explicit enablement are still required before a promoted MCP can serve runtime calls. Default built-in integration schemas remain Java-bootstrapped into the database; promoted MCP schemas are repository-driven.
