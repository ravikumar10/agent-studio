# SOP: publish MCP tools, create agents, and run them

## Purpose

This standard operating procedure is for an Agent Studio operator or developer who needs to:

1. publish an MCP server, skill, or agent definition through an approved GitHub registry;
2. synchronize and promote it into an organization;
3. configure and verify the MCP runtime provider;
4. create or update an agent using the model, skills, and MCP capabilities;
5. activate and run the agent from Studio;
6. inspect, stop, and troubleshoot the run.

The procedure uses the local UI at `http://localhost:8080`. Production uses the same logical steps with production identity, secrets, image registry, deployment adapter, and approval policy.

## Roles

| Role | Responsibility |
|---|---|
| Registry author | Adds reviewed manifests, schemas, source, and catalog entries through Git pull requests |
| Organization operator | Connects registries, promotes artifacts, configures providers, deploys adapters, and enables bindings |
| Agent author | Creates agent versions and selects model, skills, capabilities, profiles, placement, and triggers |
| Runtime operator | Monitors runs, costs, tools, failures, cancellation, and worker cleanup |

One person may hold all roles in local development. Production should separate authoring and approval for side-effecting tools.

## Preconditions

- Agent Studio is running: `./start.sh`.
- `./start.sh --status` shows PostgreSQL and Redis healthy and Studio/runtime services running.
- The browser loads `http://localhost:8080` without a bootstrap error.
- The GitHub registry has a root `catalog.json` and is reachable by the control plane.
- A usable logical model profile exists for agents that need LLM planning or synthesis.
- The MCP implementation has a deployable image/source, health endpoint, and no embedded secrets.
- You know the organization/tenant and user under which resources will be owned.

## Part A — Author or update the registry

Use [ravikumar10/agent-studio-sample-registry](https://github.com/ravikumar10/agent-studio-sample-registry) as the example layout.

### A1. Add an MCP server

Create these entries:

```text
implementations/<server>/...              # source, tests, Dockerfile
mcp/<server>/server.json                  # MCP/tool manifest
configurations/<server>.schema.json       # organization configuration form
catalog.json                              # discovery entry
```

The MCP manifest must declare:

- stable artifact ID and immutable version;
- transport and implementation source/image;
- logical tool names such as `web.fetch` or `slack.messages.send`;
- descriptions and input/output schemas;
- side-effect and risk metadata;
- a repository-relative configuration-schema path when configuration is required.

The JSON configuration schema describes service-specific fields. Mark tokens, passwords, connection strings, and other secrets with `"writeOnly": true`. Do not put real credentials or runtime URLs in the manifest.

### A2. Add a skill

Create `skills/<skill>/SKILL.md` and add a `SKILL` entry to `catalog.json`. State prerequisites, required capabilities, operating steps, safety constraints, and output expectations. Keep it provider-neutral and free of secrets.

### A3. Add an agent template

Create `agents/<agent>/agent.json` and add an `AGENT` entry to `catalog.json`. Reference only logical model profiles, skills, and capabilities. Do not reference MCP URLs, provider endpoints, API keys, or cloud SDK values.

### A4. Validate and publish

Before merging:

```bash
python3 -m json.tool catalog.json >/dev/null
find agents mcp configurations -name '*.json' -exec python3 -m json.tool '{}' \; >/dev/null
docker build -t <server>:local implementations/<server>
```

Run implementation tests, confirm declared operations exist, scan the image, and merge through the registry repository’s review process. Version changes require a new immutable version; do not silently replace a released artifact.

## Part B — Connect and promote registry artifacts

### B1. Connect registry sources

1. Open **Registries**.
2. Select **Connect GitHub repository**.
3. Enter a stable Registry ID, display name, repository URL, and branch/reference.
4. Select one registry type: **MCP**, **SKILL**, or **AGENT**.
5. Save the registry.
6. Connect the same repository again for the other required artifact types. Each registry connection filters one type.

For the sample repository, create separate MCP, skill, and agent connections pointing at the same GitHub repository.

### B2. Sync metadata

1. Select the registry card.
2. Click **Sync**.
3. Confirm the expected artifacts appear under **Discovered artifacts** in state `DISCOVERED`.

Sync reads only catalog metadata. It does not download implementation content or make tools runnable.

### B3. Pull the selected artifact

1. Locate the exact artifact ID and version.
2. Click **Pull**.
3. Confirm the state becomes `PULLED`.

For MCP artifacts, Pull also embeds the referenced configuration JSON Schema. After changing a manifest or schema, Sync and click **Pull again** before promotion.

### B4. Promote

1. Review the artifact identity, version, description, and source.
2. Click **Promote**.
3. Confirm the state becomes `PROMOTED`.
4. Verify the materialized result:

| Artifact | Expected result |
|---|---|
| MCP | Logical capabilities visible; registry integration type created; provider and bindings created disabled |
| SKILL | Skill visible in Agent Builder |
| AGENT | Agent visible in Agent catalog as a draft with a created version |

Promotion is idempotent, but it is not runtime activation. It never runs or loads repository code into the control plane.

## Part C — Deploy and configure a promoted MCP provider

### C1. Deploy the implementation

For local development, build and run the reviewed Docker implementation on the Agent Studio Compose network or add it as a Compose service. For production, build in isolation, scan and sign the image, pin a digest, deploy it through the approved Docker/Kubernetes adapter, and expose its health and MCP endpoints.

Do not enable a provider that points to source code which has not been reviewed and deployed.

### C2. Configure the named integration

1. Open **Capabilities**.
2. In **MCP provider configuration**, locate the promoted disabled provider.
3. Click **Configure**.
4. Confirm the selected integration type is the registry-generated type for this artifact.
5. Enter the runtime adapter/MCP base URL.
6. Complete all schema-driven fields. Different MCP types intentionally show different forms.
7. Enter secrets in **Encrypted credentials**. Existing values remain masked and should not be re-entered unless rotating them.
8. Keep the provider disabled while testing configuration, then save.

### C3. Verify and enable

1. Click **Verify** on the provider.
2. Confirm its health becomes healthy/configured and no credential error is shown.
3. Enable the provider.
4. Confirm each required capability binding points to the correct remote operation name and is enabled.
5. Reload **Capabilities** and verify the provider remains enabled after reload.

If an agent reports `No enabled capability provider profile is bound`, the provider or binding is still missing/disabled. If it reports `No deployed MCP adapter`, the capability exists but its implementation endpoint is not configured and reachable.

## Part D — Onboard a model

Skip this only for agents intentionally using no model.

1. Open **Model profiles**.
2. Click **Connect provider**.
3. Choose **OpenAI**, **Anthropic / Claude**, or **OpenAI-compatible**.
4. Enter a connection name, endpoint fields if applicable, and API key.
5. Use backend verification. **Save and choose model** remains unavailable until the current fields and credential validate.
6. Select a discovered model.
7. Create a logical profile ID such as `balanced-text` or `analysis-primary`.
8. Reload the page and confirm both connection and logical profile remain visible.

Agents select the logical profile, not the raw provider connection or endpoint. API keys are stored encrypted and returned masked.

## Part E — Create an agent

### E1. Start the builder

1. Open **Agents**.
2. Click **New agent**.
3. Choose **Compose an agent** for a platform-configured agent, or **Import from repository** for reviewed custom code that will run in isolation.

### E2. Define identity and interaction

Enter:

- stable Agent ID;
- display name, owner team, description, and tags;
- interface: **Run once**, **Chat**, or **Run once + chat**;
- topology: normally **Single agent**;
- trigger: normally **On demand**.

Scheduled, event-driven, and multi-agent configuration can be stored, but do not treat them as operational until Temporal scheduling/delegation is deployed and verified.

### E3. Select model, skills, and tools

1. Choose the **Logical model profile** dropdown.
2. Select the required skills.
3. Under **MCP capabilities**, use **Relevant to this agent** or a capability-family filter.
4. Select capabilities in the intended execution order.
5. For each capability, select the named provider profile or leave **Automatic healthy provider** only when an unambiguous healthy binding exists.

For a flow such as database → web enrichment → chart → Slack delivery, order the capabilities accordingly and bind every one to a verified provider. The LLM planner may skip unnecessary tools within its bounded loop, but it cannot invent unavailable data.

### E4. Configure memory and execution

Choose memory/cache behavior and execution placement:

- **Automatic**: runtime selects the safe supported placement;
- **In process · lightweight**: config agents and trusted built-in adapters only;
- **Docker · isolated worker**: custom/heavy agent execution;
- **Kubernetes pod · scalable**: stores portable deployment intent unless a real cluster adapter is configured.

For scheduled configuration, enter an explicit schedule and saved runtime input/prompt. The catalog description alone is not scheduled input.

### E5. Save and activate

1. Save the agent. Editing an existing agent publishes a new immutable version.
2. Review the new version and runtime configuration.
3. Apply lifecycle transitions in order where required: validate, optional canary, then activate.
4. Confirm the agent card identifies an active version before running.

Never mutate a version used by an existing run. New runs resolve the newly active version; existing runs remain pinned.

## Part F — Run or chat with the agent

### F1. Start the workspace

1. From **Agent catalog**, click **Run** on an agent with an active version.
2. The **Agent workspace** opens as a full page/tab.
3. Enter every task input in the chat composer. Include URLs, database question constraints, report requirements, recipients/channels, and requested charts in the message.
4. Click **Send** or **Run agent**.

Do not use fixed hidden input boxes for agent-specific values. The agent loop interprets the message and chooses only attached, permitted tools.

### F2. Observe execution

The workspace should show live status and the final unified response. Detailed planner, model, MCP, cache, usage, and terminal events belong in **Runs**:

1. Open **Runs**.
2. Select the full run ID.
3. Verify all model/tool calls for that request share the same run ID.
4. Confirm MCP output is present before accepting factual claims that require tools.
5. Open **Observability** for cost, tokens, model calls, MCP calls/cache hits, duration, and failures over the selected time range.

### F3. Continue chat

For chat-capable agents, send the next message in the same workspace. Redis hot memory may preserve recent context/tool evidence and reduce repetitive calls, subject to TTL, policy, and freshness. The model should still call tools when the cached evidence is missing or stale.

### F4. Stop and close

- Click **Stop** to cancel an active run.
- Click **Close workspace** to leave the workspace and cancel any active workspace run.
- Verify the run reaches `CANCELLED` or another terminal state.
- For Docker placement, confirm the ephemeral worker container is removed after completion/cancellation.

## Part G — Acceptance checklist

- [ ] Model connection verified and logical model profile selectable.
- [ ] Registry Sync, Pull, and Promote completed for required MCP/skill/agent artifacts.
- [ ] Promoted MCP implementation deployed and reachable.
- [ ] Provider configuration persists after reload; credentials are masked.
- [ ] Provider health verification passes.
- [ ] Required capability bindings are enabled.
- [ ] Agent version binds only logical capability IDs and a logical model profile.
- [ ] Every capability has the intended named provider or an unambiguous healthy automatic route.
- [ ] Agent version is active.
- [ ] Run creates live SSE updates and a final response.
- [ ] Tool-required facts are grounded in MCP results.
- [ ] Runs groups all events under the full run ID.
- [ ] Usage and cost appear in Observability.
- [ ] Stop/close reaches a terminal state and removes isolated workers.

## Troubleshooting

| Symptom | Likely cause | Corrective action |
|---|---|---|
| Artifact not visible after Sync | Wrong registry type, branch, catalog path, or GitHub access | Verify source and connect one registry per artifact type; Sync again |
| Promote unavailable | Artifact not pulled | Pull the selected artifact first |
| Generated integration has only URL/health | Artifact was pulled before schema support or manifest lacks `configurationSchema` | Fix manifest/schema, Sync, Pull again, Promote again |
| Capability missing in Agent Builder | Artifact not promoted or provider binding absent | Promote; reload catalogs; inspect provider bindings |
| Provider verification fails | Wrong endpoint/health path/credential or unreachable container/network | Test health from the control-plane network; correct and re-verify |
| `modelId is required` | Logical model profile has no selected discovered model | Edit/recreate the profile with a concrete model ID |
| Only LLM prose, no MCP evidence | Capability not attached, provider not enabled/bound, or planner deemed tool unnecessary | Check version capabilities, named profile, bindings, and run events; make grounding requirement explicit |
| `No enabled capability provider profile is bound` | Disabled/missing provider binding | Configure, verify, and enable provider plus binding |
| Docker image not found | Runtime worker image was not built | Run `./start.sh` without `--no-build` |
| Public URL rejected | URL resolves to local/private/metadata address or restricted allow-list | Use a public target or intentionally update the provider allow-list; never disable SSRF controls |
| Scheduled agent never runs | Temporal scheduler is not operational | Use on-demand runs or implement/verify Temporal scheduling before claiming support |
| Multi-agent members do not execute | Stored topology exists but delegation workflow is not operational | Use single-agent mode or complete the Temporal multi-agent workflow |

## API sequence for automation

The UI is the normal operator path. Automation follows the same order with `X-Tenant-Id` and `X-User-Id` headers:

```text
POST /api/v1/registries
POST /api/v1/registries/{registryId}/sync
POST /api/v1/registries/{registryId}/artifacts/{artifactId}/pull
POST /api/v1/registries/{registryId}/artifacts/{artifactId}/promote

PUT  /api/v1/capability-providers/{providerId}
POST /api/v1/capability-providers/{providerId}/verify
POST /api/v1/capability-providers/{providerId}/bindings

POST /api/v1/agents
POST /api/v1/agents/{agentId}/versions
PUT  /api/v1/agents/{agentId}/versions/{version}/runtime-config
POST /api/v1/agents/{agentId}/versions/{version}/validate
POST /api/v1/agents/{agentId}/versions/{version}/activate

POST /api/v1/runs
GET  /api/v1/runs/{runId}/events
POST /api/v1/runs/{runId}/cancel
```

Do not bypass verification or directly edit PostgreSQL to make an artifact appear enabled.
