# Registry promotion pipeline

Repository synchronization is deliberately separated from runtime activation. A GitHub repository is untrusted discovery input until a user explicitly pulls and promotes an artifact.

## Lifecycle

1. **Sync** reads `catalog.json` and records matching `AGENT`, `MCP`, or `SKILL` entries as `DISCOVERED`.
2. **Pull** downloads the selected artifact. For MCP artifacts it also downloads and embeds the referenced configuration JSON Schema. Content is bounded to 1 MB and paths may not be absolute or contain `..`.
3. **Promote** validates and materializes the artifact into tenant-scoped platform tables.
4. MCP provider templates and bindings remain disabled until configuration, deployment, and verification are complete.

Artifact states are `DISCOVERED`, `PULLED`, `PROMOTED`, and `FAILED`. Promotion is idempotent: promoting an existing version updates its materialized records.

## Materialization

### MCP

Each declared tool becomes a logical capability. Promotion also creates a disabled provider template and disabled bindings connecting each logical capability to its remote operation name. This makes capabilities visible in Agent Builder without executing repository code.

If the entry references a JSON configuration schema, Pull stores it with the immutable artifact snapshot. Promote converts it into an organization-scoped integration type. Studio renders its strings, numbers, booleans, enums, and write-only secrets at runtime; no artifact-specific form is compiled into the UI. The generated type also includes a required runtime `baseUrl` and an optional health path for binding the deployed MCP adapter.

### Skill

The skill is upserted into `available_skills` with a GitHub reference and becomes selectable after the UI reloads.

### Agent

The template becomes a `DRAFT` agent and a `CREATED` immutable version. Its logical capabilities and skills are translated into the internal agent version contract. The user must review and activate the version before execution.

## API

```text
POST /api/v1/registries/{registryId}/sync
POST /api/v1/registries/{registryId}/artifacts/{artifactId}/pull
POST /api/v1/registries/{registryId}/artifacts/{artifactId}/promote
```

All operations require `X-Tenant-Id`; promotion also attributes ownership using `X-User-Id`.

## Safety boundary

Promotion never builds or starts arbitrary repository code in the Control Plane. A later deployment workflow must build in an isolated builder, scan and sign the image, pin its digest, deploy it to an isolated runtime, verify readiness, and only then enable the provider and bindings.
