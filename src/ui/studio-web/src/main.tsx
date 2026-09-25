import React from "react";
import ReactDOM from "react-dom/client";
import "./styles.css";
import "./registry.css";
import "./stream.css";
import "./builder.css";
import "./deployments.css";
import "./providers.css";
import "./runtime-config.css";
import PlaygroundPanel from "./PlaygroundPanel";

type Agent = {
  id: string;
  displayName: string;
  description: string;
  ownerTeam: string;
  status: string;
  tags: string[];
  interactionMode: "TASK" | "CHAT" | "TASK_AND_CHAT";
  topology: "SINGLE_AGENT" | "MULTI_AGENT";
  triggerMode: "ON_DEMAND" | "SCHEDULED" | "EVENT_DRIVEN";
};
type Registry = {
  registryId: string;
  registryType: "AGENT" | "MCP" | "SKILL";
  displayName: string;
  sourceType: string;
  sourceUri: string;
  owner: string;
  discoveryPattern: string;
  status: string;
  metadata: Record<string, unknown>;
  syncStatus?: string;
  lastSyncedAt?: string;
  syncError?: string;
};
type Artifact = {
  id: string;
  type: "AGENT" | "MCP" | "SKILL";
  name: string;
  version: string;
  path: string;
  description: string;
  state: string;
  pulledAt?: string;
};
type Run = {
  runId: string;
  agentId: string;
  agentVersion: string;
  status: string;
  createdAt: string;
  output: Record<string, unknown>;
};
type Capability = {
  capabilityId: string;
  displayName: string;
  description: string;
};
type Skill = {
  skillId: string;
  displayName: string;
  description: string;
  reference: string;
  tags: string[];
};
type AgentVersion = {
  agentId: string;
  version: string;
  runtimeType: "CONFIG" | "REMOTE_HTTP";
  hostingMode: string;
  artifactRef: string;
  remoteEndpointRef?: string;
  toolCapabilitiesRequired: string[];
  agentCapabilitiesRequired: string[];
  modelProfile?: string;
  promptRef?: string;
  lifecycle: string;
};
type ModelProfile = {
  profileId: string;
  id?: string;
  connectionId?: string;
  modelId?: string;
  qualityTier?: string;
  requiredFeatures?: string[];
};
type ModelConnection = {
  connectionId: string;
  displayName: string;
  provider: "OPENAI" | "ANTHROPIC" | "OPENAI_COMPATIBLE";
  baseUrl: string;
  secretRef: string;
  enabled: boolean;
};
type ModelOption = { id: string; displayName: string };
type UserProfile = {
  userId: string;
  displayName: string;
  email?: string;
  preferences: Record<string, unknown>;
};
type DeploymentEnvironment = {
  environmentId: string;
  displayName: string;
  targetType: "STUDIO" | "KUBERNETES";
  cloudProvider: string;
  namespace: string;
  adapterConfig: Record<string, unknown>;
  credentialRef?: string;
  status: string;
};
type DeploymentPlan = {
  deploymentId: string;
  environmentId: string;
  agentId: string;
  agentVersion: string;
  deploymentMode: string;
  desiredState: string;
  observedState: string;
  imageRef?: string;
  replicas: number;
  manifestYaml: string;
  configuration: Record<string, unknown>;
  lastError?: string;
};
type BrainProfile = {
  profileId: string;
  displayName: string;
  modelProfile?: string;
  policy: Record<string, unknown>;
  enabled: boolean;
};
type CapabilityProvider = {
  providerId: string;
  displayName: string;
  providerType: string;
  transport: string;
  endpointRef?: string;
  authType: string;
  secretRef?: string;
  configuration: Record<string, unknown>;
  environment: string;
  healthStatus: string;
  lastError?: string;
  enabled: boolean;
  integrationKind: string;
  schemaVersion: number;
  credentials: Record<string, string>;
};
type IntegrationField = {
  key: string;
  label: string;
  inputType: "TEXT" | "NUMBER" | "URL" | "SELECT" | "BOOLEAN" | "PASSWORD";
  required: boolean;
  secret: boolean;
  placeholder?: string;
  defaultValue?: string;
  options: string[];
};
type IntegrationType = {
  kind: string;
  category: string;
  displayName: string;
  providerType: string;
  transport: string;
  schemaVersion: number;
  fields: IntegrationField[];
  credentialFields: IntegrationField[];
  suggestedCapabilities: string[];
};
type CapabilityBinding = {
  capabilityId: string;
  providerId: string;
  remoteOperationName: string;
  providerVersion: string;
  routingWeight: number;
  enabled: boolean;
};
type AgentRuntimeConfig = {
  executionPlacement: string;
  triggerType: string;
  triggerConfiguration: Record<string, unknown>;
  resourceConfiguration: Record<string, unknown>;
  capabilityProfiles: Record<string, string>;
};
type Page =
  | "Agents"
  | "Deployments"
  | "Registries"
  | "Memory"
  | "Runs"
  | "Capabilities"
  | "Model profiles"
  | "User profile";
const tenant = "local-development";
const userId = "studio-user";
const headers = {
  "Content-Type": "application/json",
  "X-Tenant-Id": tenant,
  "X-User-Id": userId,
};
async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const r = await fetch(path, {
    ...init,
    headers: { ...headers, ...init?.headers },
  });
  if (!r.ok) throw new Error(`${r.status} ${await r.text()}`);
  if (r.status === 204) return undefined as T;
  const data = await r.json();
  if (path === "/api/v1/model-profiles" && Array.isArray(data))
    return data.map((profile) => ({ ...profile, profileId: profile.id })) as T;
  return data as T;
}

function App() {
  const [page, setPage] = React.useState<Page>("Agents");
  const [agents, setAgents] = React.useState<Agent[]>([]);
  const [registries, setRegistries] = React.useState<Registry[]>([]);
  const [runs, setRuns] = React.useState<Run[]>([]);
  const [capabilities, setCapabilities] = React.useState<Capability[]>([]);
  const [skills, setSkills] = React.useState<Skill[]>([]);
  const [profiles, setProfiles] = React.useState<ModelProfile[]>([]);
  const [defaultModel, setDefaultModel] = React.useState("");
  const [playground, setPlayground] = React.useState<Agent | null>(null);
  const [streamState, setStreamState] = React.useState<
    "connecting" | "live" | "retrying"
  >("connecting");
  const [error, setError] = React.useState("");
  const [query, setQuery] = React.useState("");
  const [modal, setModal] = React.useState(false);
  const [editing, setEditing] = React.useState<Agent | null>(null);
  const [notice, setNotice] = React.useState("");
  const load = React.useCallback(async () => {
    try {
      setError("");
      const [a, r, u, c, s, m, p] = await Promise.all([
        api<Agent[]>("/api/v1/agents"),
        api<Registry[]>("/api/v1/registries"),
        api<Run[]>("/api/v1/runs"),
        api<Capability[]>("/api/v1/capabilities"),
        api<Skill[]>("/api/v1/skills"),
        api<ModelProfile[]>("/api/v1/model-profiles"),
        api<UserProfile>("/api/v1/user-profile"),
      ]);
      setAgents(a);
      setRegistries(r);
      setRuns(u);
      setCapabilities(c);
      setSkills(s);
      setProfiles(m);
      const preferred = String(p.preferences?.defaultModelProfile || "");
      setDefaultModel(
        m.some((profile) => profile.profileId === preferred)
          ? preferred
          : m[0]?.profileId || "",
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);
  React.useEffect(() => {
    load();
  }, [load]);
  React.useEffect(() => {
    const source = new EventSource(
      `/api/v1/runs/stream?tenantId=${encodeURIComponent(tenant)}`,
    );
    source.onopen = () => setStreamState("live");
    source.onerror = () => setStreamState("retrying");
    source.addEventListener("run", (event) => {
      const message = JSON.parse((event as MessageEvent).data) as { run: Run };
      setRuns((current) =>
        [
          message.run,
          ...current.filter((run) => run.runId !== message.run.runId),
        ]
          .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
          .slice(0, 100),
      );
    });
    return () => source.close();
  }, []);
  const filtered = agents.filter((a) =>
    `${a.id} ${a.displayName} ${a.ownerTeam}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  );
  async function deleteAgent(a: Agent) {
    if (
      !window.confirm(
        `Remove ${a.displayName} from the agent catalog? Historical runs will be retained.`,
      )
    )
      return;
    try {
      await api(`/api/v1/agents/${encodeURIComponent(a.id)}`, {
        method: "DELETE",
      });
      setAgents((current) => current.filter((item) => item.id !== a.id));
      setPlayground((current) => (current?.id === a.id ? null : current));
      setNotice(`${a.displayName} removed from the catalog.`);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }
  return (
    <div className="shell">
      <aside>
        <button className="brand" onClick={() => setPage("Agents")}>
          Agent Studio
        </button>
        <nav>
          {(
            [
              "Agents",
              "Deployments",
              "Registries",
              "Memory",
              "Runs",
              "Capabilities",
              "Model profiles",
              "User profile",
            ] as Page[]
          ).map((n) => (
            <button
              key={n}
              className={page === n ? "active" : ""}
              onClick={() => setPage(n)}
            >
              {n}
            </button>
          ))}
        </nav>
        <div className="tenant">
          Tenant
          <br />
          <strong>{tenant}</strong>
          <br />
          User
          <br />
          <strong>{userId}</strong>
        </div>
      </aside>
      <main>
        <header>
          <div>
            <p className="eyebrow">CONTROL PLANE</p>
            <h1>{page}</h1>
            <p>{subtitle(page)}</p>
          </div>
          {page === "Agents" && (
            <button
              className="primary"
              onClick={() => {
                setEditing(null);
                setModal(true);
              }}
            >
              New agent
            </button>
          )}
        </header>
        {error && (
          <div className="notice error">
            API error: {error}
            <button onClick={load}>Retry</button>
          </div>
        )}
        {notice && (
          <div className="notice success">
            {notice}
            <button onClick={() => setNotice("")}>Dismiss</button>
          </div>
        )}
        {page === "Agents" && (
          <>
            <section className="creation-paths">
              <article>
                <span>NO-CODE</span>
                <h2>Compose an agent</h2>
                <p>
                  Combine a model profile, governed MCP capabilities, skills,
                  memory, and policies.
                </p>
                <button
                  className="primary"
                  onClick={() => {
                    setEditing(null);
                    setModal(true);
                  }}
                >
                  Compose agent
                </button>
              </article>
              <article>
                <span>BRING YOUR OWN CODE</span>
                <h2>Import from repository</h2>
                <p>
                  Register Java, JavaScript, Python, or framework-based agents
                  behind the same invocation contract.
                </p>
                <button
                  onClick={() => {
                    setEditing(null);
                    setModal(true);
                  }}
                >
                  Import code agent
                </button>
              </article>
            </section>
            <section className="stats">
              <article>
                <strong>{agents.length}</strong>
                <span>Registered agents</span>
              </article>
              <article>
                <strong>
                  {agents.filter((a) => a.status === "ACTIVE").length}
                </strong>
                <span>Active definitions</span>
              </article>
              <article>
                <strong>{runs.length}</strong>
                <span>Recent runs</span>
              </article>
            </section>
            <section className="panel">
              <div className="panel-title">
                <h2>Agent catalog</h2>
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  aria-label="Search agents"
                  placeholder="Search agents…"
                />
              </div>
              {filtered.length === 0 ? (
                <Empty title="No matching agents" />
              ) : (
                <div className="grid">
                  {filtered.map((a) => (
                    <article className="card" key={a.id}>
                      <div>
                        <span className={`status ${a.status.toLowerCase()}`}>
                          {a.status}
                        </span>
                        <h3>{a.displayName}</h3>
                        <code>{a.id}</code>
                      </div>
                      <p>{a.description || "No description provided."}</p>
                      <div className="agent-contract">
                        <span>
                          {a.interactionMode === "TASK"
                            ? "RUN ONCE"
                            : a.interactionMode === "CHAT"
                              ? "CHAT"
                              : "TASK + CHAT"}
                        </span>
                        <span>
                          {a.topology === "MULTI_AGENT"
                            ? "MULTI-AGENT"
                            : "SINGLE AGENT"}
                        </span>
                        <span>{a.triggerMode?.replace("_", " ")}</span>
                      </div>
                      <footer>
                        <span>{a.ownerTeam}</span>
                        <span>{a.tags?.join(" · ")}</span>
                      </footer>
                      <div className="card-actions">
                        <button onClick={() => deleteAgent(a)}>Delete</button>
                        <button
                          onClick={() => {
                            setEditing(a);
                            setModal(true);
                          }}
                        >
                          Configure
                        </button>
                        <button
                          className="primary"
                          disabled={a.status !== "ACTIVE"}
                          onClick={() => setPlayground(a)}
                        >
                          {a.interactionMode === "TASK"
                            ? "Run task"
                            : a.interactionMode === "CHAT"
                              ? "Open chat"
                              : "Open console"}
                        </button>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          </>
        )}
        {page === "Deployments" && (
          <DeploymentsPage
            agents={agents}
            profiles={profiles}
            onNotice={setNotice}
          />
        )}
        {page === "Registries" && (
          <RegistryPage
            registries={registries}
            reload={load}
            onNotice={setNotice}
          />
        )}{" "}
        {page === "Memory" && <MemoryPage onNotice={setNotice} />}{" "}
        {page === "Runs" && <RunsPage runs={runs} streamState={streamState} />}{" "}
        {page === "Capabilities" && (
          <CapabilityProvidersPage
            capabilities={capabilities}
            onNotice={setNotice}
          />
        )}{" "}
        {page === "Model profiles" && (
          <ModelProfilesPage
            profiles={profiles}
            reload={load}
            onNotice={setNotice}
          />
        )}
        {page === "User profile" && (
          <UserProfilePage
            profiles={profiles}
            onNotice={setNotice}
            onChanged={load}
          />
        )}
        {modal && (
          <AgentModal
            editing={editing}
            agents={agents}
            capabilities={capabilities}
            availableSkills={skills}
            profiles={profiles}
            defaultModel={defaultModel}
            close={() => setModal(false)}
            saved={(message) => {
              setModal(false);
              setEditing(null);
              setNotice(message);
              load();
            }}
          />
        )}
        {playground && (
          <PlaygroundPanel
            agent={playground}
            close={() => setPlayground(null)}
            onCatalogChanged={load}
          />
        )}
      </main>
    </div>
  );
}
function subtitle(p: Page) {
  return p === "Deployments"
    ? "Plan, schedule, and orchestrate portable agent workloads."
    : p === "Registries"
      ? "Govern agent, MCP, and skill sources."
      : p === "Memory"
        ? "Hot Redis context and durable PostgreSQL knowledge."
        : p === "Runs"
          ? "Inspect pinned versions and execution outcomes."
          : p === "User profile"
            ? "Manage your identity and non-secret Studio defaults."
            : "Build centrally. Govern centrally. Run anywhere.";
}
function Empty({ title }: { title: string }) {
  return (
    <div className="empty">
      <h3>{title}</h3>
      <p>Create or import an entry to populate this catalog.</p>
    </div>
  );
}
function DeploymentsPage({
  agents,
  profiles,
  onNotice,
}: {
  agents: Agent[];
  profiles: ModelProfile[];
  onNotice: (v: string) => void;
}) {
  const [environments, setEnvironments] = React.useState<
    DeploymentEnvironment[]
  >([]);
  const [deployments, setDeployments] = React.useState<DeploymentPlan[]>([]);
  const [brains, setBrains] = React.useState<BrainProfile[]>([]);
  const [addingEnv, setAddingEnv] = React.useState(false);
  const [planning, setPlanning] = React.useState(false);
  const [busy, setBusy] = React.useState("");
  const [error, setError] = React.useState("");
  const refresh = React.useCallback(async () => {
    try {
      setError("");
      const [e, d, b] = await Promise.all([
        api<DeploymentEnvironment[]>("/api/v1/deployments/environments"),
        api<DeploymentPlan[]>("/api/v1/deployments"),
        api<BrainProfile[]>("/api/v1/deployments/brain"),
      ]);
      setEnvironments(e);
      setDeployments(d);
      setBrains(b);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);
  React.useEffect(() => {
    refresh();
  }, [refresh]);
  async function applyPlan(d: DeploymentPlan) {
    setBusy(d.deploymentId);
    try {
      const result = await api<DeploymentPlan>(
        `/api/v1/deployments/${d.deploymentId}/apply`,
        { method: "POST" },
      );
      await refresh();
      onNotice(
        result.deploymentMode === "STUDIO"
          ? `${d.agentId} is running in Agent Studio.`
          : `${d.agentId} was handed to the configured Kubernetes deployment adapter.`,
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy("");
    }
  }
  async function removePlan(d: DeploymentPlan) {
    if (!window.confirm(`Delete deployment plan for ${d.agentId}?`)) return;
    await api(`/api/v1/deployments/${d.deploymentId}`, { method: "DELETE" });
    await refresh();
    onNotice("Deployment plan deleted.");
  }
  return (
    <>
      <section className="stats">
        <article>
          <strong>{environments.length}</strong>
          <span>Deployment environments</span>
        </article>
        <article>
          <strong>
            {
              deployments.filter((d) =>
                ["RUNNING", "APPLY_REQUESTED"].includes(d.observedState),
              ).length
            }
          </strong>
          <span>Active orchestration</span>
        </article>
        <article>
          <strong>{brains.length}</strong>
          <span>Brain profiles</span>
        </article>
      </section>
      {error && (
        <div className="notice error">
          Deployment error: {error}
          <button onClick={refresh}>Retry</button>
        </div>
      )}
      <section className="panel">
        <div className="panel-title">
          <div>
            <h2>Deployment environments</h2>
            <p className="muted">
              Cloud-neutral Kubernetes targets with provider configuration and
              secret references.
            </p>
          </div>
          <button className="primary" onClick={() => setAddingEnv(true)}>
            Add environment
          </button>
        </div>
        <div className="model-grid">
          {environments.map((e) => (
            <article key={e.environmentId}>
              <span className="kind model">{e.cloudProvider}</span>
              <h3>{e.displayName}</h3>
              <code>{e.environmentId}</code>
              <p>
                {e.targetType} · namespace {e.namespace}
              </p>
              <small>
                {e.credentialRef || "No cluster credential required/configured"}
              </small>
            </article>
          ))}
        </div>
      </section>
      <section className="panel">
        <div className="panel-title">
          <div>
            <h2>Deployment plans</h2>
            <p className="muted">
              The Brain generates a pinned, reviewable plan before any side
              effect.
            </p>
          </div>
          <button
            className="primary"
            disabled={!agents.length || !environments.length}
            onClick={() => setPlanning(true)}
          >
            Plan deployment
          </button>
        </div>
        {deployments.length === 0 ? (
          <Empty title="No deployment plans" />
        ) : (
          <div className="deployment-list">
            {deployments.map((d) => (
              <article key={d.deploymentId}>
                <div>
                  <span
                    className={`status ${d.observedState === "RUNNING" ? "active" : ""}`}
                  >
                    {d.observedState.replaceAll("_", " ")}
                  </span>
                  <h3>
                    {d.agentId} <small>{d.agentVersion}</small>
                  </h3>
                  <p>
                    {d.environmentId} · {d.deploymentMode} · {d.replicas}{" "}
                    replica(s)
                  </p>
                </div>
                <div className="actions">
                  <button onClick={() => removePlan(d)}>Delete</button>
                  <details>
                    <summary>Manifest</summary>
                    <pre>{d.manifestYaml}</pre>
                  </details>
                  <button
                    className="primary"
                    disabled={
                      busy === d.deploymentId ||
                      ["RUNNING", "APPLY_REQUESTED"].includes(d.observedState)
                    }
                    onClick={() => applyPlan(d)}
                  >
                    {busy === d.deploymentId
                      ? "Applying…"
                      : d.deploymentMode === "STUDIO"
                        ? "Run in Studio"
                        : "Deploy"}
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
      <section className="panel brain-panel">
        <div className="panel-title">
          <div>
            <p className="eyebrow">THE BRAIN</p>
            <h2>Bounded orchestration</h2>
          </div>
          <span className="status active">POLICY DRIVEN</span>
        </div>
        {brains.map((b) => (
          <article key={b.profileId}>
            <div>
              <h3>{b.displayName}</h3>
              <code>{b.profileId}</code>
              <p>Model: {b.modelProfile || "deterministic by default"}</p>
            </div>
            <div className="brain-flow">
              <span>Resolve pinned version</span>
              <b>→</b>
              <span>Build manifest</span>
              <b>→</b>
              <span>Policy + health gate</span>
              <b>→</b>
              <span>Adapter apply</span>
              <b>→</b>
              <span>Observe / rollback</span>
            </div>
            <pre>{JSON.stringify(b.policy, null, 2)}</pre>
          </article>
        ))}
      </section>
      {addingEnv && (
        <EnvironmentModal
          close={() => setAddingEnv(false)}
          saved={async () => {
            setAddingEnv(false);
            await refresh();
            onNotice("Deployment environment saved.");
          }}
        />
      )}
      {planning && (
        <DeploymentModal
          agents={agents}
          environments={environments}
          brains={brains}
          profiles={profiles}
          close={() => setPlanning(false)}
          saved={async () => {
            setPlanning(false);
            await refresh();
            onNotice("Deployment manifest and orchestration plan stored.");
          }}
        />
      )}
    </>
  );
}
function EnvironmentModal({
  close,
  saved,
}: {
  close: () => void;
  saved: () => void;
}) {
  const [id, setId] = React.useState("azure-aks-prod");
  const [name, setName] = React.useState("Azure AKS production");
  const [target, setTarget] = React.useState<"STUDIO" | "KUBERNETES">(
    "KUBERNETES",
  );
  const [provider, setProvider] = React.useState("AZURE");
  const [namespace, setNamespace] = React.useState("agent-studio");
  const [subscription, setSubscription] = React.useState("");
  const [resourceGroup, setResourceGroup] = React.useState("");
  const [cluster, setCluster] = React.useState("");
  const [region, setRegion] = React.useState("");
  const [identity, setIdentity] = React.useState("");
  const [credentialRef, setCredentialRef] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const adapterConfig =
        provider === "AZURE"
          ? {
              subscriptionId: subscription,
              resourceGroup,
              clusterName: cluster,
              region,
              workloadIdentityClientId: identity,
            }
          : {};
      await api("/api/v1/deployments/environments", {
        method: "POST",
        body: JSON.stringify({
          environmentId: id,
          displayName: name,
          targetType: target,
          cloudProvider: provider,
          namespace,
          adapterConfig,
          credentialRef: credentialRef || null,
        }),
      });
      saved();
    } finally {
      setBusy(false);
    }
  }
  function changeTarget(value: "STUDIO" | "KUBERNETES") {
    setTarget(value);
    if (value === "STUDIO") {
      setProvider("LOCAL");
      setId("studio-local-custom");
      setName("Agent Studio runtime");
    } else if (provider === "LOCAL") setProvider("AZURE");
  }
  return (
    <div className="backdrop">
      <form className="modal builder" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">DEPLOYMENT ADAPTER</p>
            <h2>Add environment</h2>
          </div>
          <button type="button" aria-label="Close" onClick={close}>
            ×
          </button>
        </div>
        <div className="form-grid">
          <label>
            Target
            <select
              value={target}
              onChange={(e) => changeTarget(e.target.value as typeof target)}
            >
              <option value="STUDIO">Run in Agent Studio</option>
              <option value="KUBERNETES">Kubernetes cluster</option>
            </select>
          </label>
          <label>
            Cloud provider
            <select
              value={provider}
              disabled={target === "STUDIO"}
              onChange={(e) => setProvider(e.target.value)}
            >
              <option value="LOCAL">Local / Studio</option>
              <option value="AZURE">Azure AKS</option>
              <option value="AWS">AWS EKS</option>
              <option value="GCP">Google GKE</option>
              <option value="OPENSHIFT">OpenShift</option>
              <option value="ON_PREM">Generic / on-prem</option>
            </select>
          </label>
          <label>
            Namespace
            <input
              required
              value={namespace}
              onChange={(e) => setNamespace(e.target.value)}
            />
          </label>
        </div>
        <div className="form-grid">
          <label>
            Environment ID
            <input
              required
              value={id}
              onChange={(e) => setId(e.target.value)}
            />
          </label>
          <label>
            Display name
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
        </div>
        {provider === "AZURE" && (
          <>
            <div className="form-grid">
              <label>
                Azure subscription ID
                <input
                  required
                  value={subscription}
                  onChange={(e) => setSubscription(e.target.value)}
                />
              </label>
              <label>
                Resource group
                <input
                  required
                  value={resourceGroup}
                  onChange={(e) => setResourceGroup(e.target.value)}
                />
              </label>
              <label>
                AKS cluster
                <input
                  required
                  value={cluster}
                  onChange={(e) => setCluster(e.target.value)}
                />
              </label>
            </div>
            <div className="form-grid">
              <label>
                Azure region
                <input
                  value={region}
                  onChange={(e) => setRegion(e.target.value)}
                />
              </label>
              <label>
                Workload identity client ID
                <input
                  value={identity}
                  onChange={(e) => setIdentity(e.target.value)}
                />
              </label>
            </div>
          </>
        )}{" "}
        {target === "KUBERNETES" && (
          <label>
            Cluster credential reference
            <input
              value={credentialRef}
              onChange={(e) => setCredentialRef(e.target.value)}
              placeholder="k8secret://agent-studio/cluster-access"
            />
            <small>
              Only a SecretProvider reference is stored. Raw kubeconfig and
              cloud credentials are rejected.
            </small>
          </label>
        )}
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Saving…" : "Save environment"}
          </button>
        </div>
      </form>
    </div>
  );
}
function DeploymentModal({
  agents,
  environments,
  brains,
  profiles,
  close,
  saved,
}: {
  agents: Agent[];
  environments: DeploymentEnvironment[];
  brains: BrainProfile[];
  profiles: ModelProfile[];
  close: () => void;
  saved: () => void;
}) {
  const [agent, setAgent] = React.useState(
    agents.find((a) => a.status === "ACTIVE")?.id || agents[0]?.id || "",
  );
  const [environment, setEnvironment] = React.useState(
    environments[0]?.environmentId || "",
  );
  const selected = environments.find((e) => e.environmentId === environment);
  const [image, setImage] = React.useState(
    "ghcr.io/ravikumar10/agent-worker:latest",
  );
  const [replicas, setReplicas] = React.useState("1");
  const [frequency, setFrequency] = React.useState("ON_DEMAND");
  const [customCron, setCustomCron] = React.useState("0 9 * * 1-5");
  const [timeZone, setTimeZone] = React.useState("UTC");
  const [brain, setBrain] = React.useState(
    brains[0]?.profileId || "default-brain",
  );
  const [busy, setBusy] = React.useState(false);
  const cron =
    frequency === "HOURLY"
      ? "0 * * * *"
      : frequency === "DAILY"
        ? "0 9 * * *"
        : frequency === "WEEKDAYS"
          ? "0 9 * * 1-5"
          : frequency === "CUSTOM"
            ? customCron
            : "";
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api("/api/v1/deployments", {
        method: "POST",
        body: JSON.stringify({
          environmentId: environment,
          agentId: agent,
          imageRef: selected?.targetType === "KUBERNETES" ? image : null,
          replicas: Number(replicas),
          cronExpression: cron || null,
          timeZone,
          brainProfile: brain,
          scheduleInput: { source: "studio" },
        }),
      });
      saved();
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="backdrop">
      <form className="modal builder" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">BRAIN DEPLOYMENT PLAN</p>
            <h2>Plan agent deployment</h2>
          </div>
          <button type="button" aria-label="Close" onClick={close}>
            ×
          </button>
        </div>
        <div className="form-grid">
          <label>
            Agent
            <select
              required
              value={agent}
              onChange={(e) => setAgent(e.target.value)}
            >
              {agents
                .filter((a) => a.status === "ACTIVE")
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.displayName}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Environment
            <select
              required
              value={environment}
              onChange={(e) => setEnvironment(e.target.value)}
            >
              {environments.map((e) => (
                <option key={e.environmentId} value={e.environmentId}>
                  {e.displayName} · {e.cloudProvider}
                </option>
              ))}
            </select>
          </label>
          <label>
            Orchestration brain
            <select value={brain} onChange={(e) => setBrain(e.target.value)}>
              {brains.map((b) => (
                <option key={b.profileId} value={b.profileId}>
                  {b.displayName}
                </option>
              ))}
            </select>
          </label>
        </div>
        {selected?.targetType === "KUBERNETES" && (
          <div className="form-grid">
            <label>
              Container image
              <input
                required
                value={image}
                onChange={(e) => setImage(e.target.value)}
              />
            </label>
            <label>
              Replicas
              <select
                value={replicas}
                onChange={(e) => setReplicas(e.target.value)}
              >
                {["1", "2", "3", "5", "10"].map((v) => (
                  <option key={v}>{v}</option>
                ))}
              </select>
            </label>
          </div>
        )}
        <div className="form-grid">
          <label>
            Frequency
            <select
              value={frequency}
              onChange={(e) => setFrequency(e.target.value)}
            >
              <option value="ON_DEMAND">On demand</option>
              <option value="HOURLY">Hourly</option>
              <option value="DAILY">Daily at 09:00</option>
              <option value="WEEKDAYS">Weekdays at 09:00</option>
              <option value="CUSTOM">Custom cron</option>
            </select>
          </label>
          {frequency === "CUSTOM" && (
            <label>
              Cron expression
              <input
                required
                value={customCron}
                onChange={(e) => setCustomCron(e.target.value)}
              />
            </label>
          )}
          {frequency !== "ON_DEMAND" && (
            <label>
              Time zone
              <select
                value={timeZone}
                onChange={(e) => setTimeZone(e.target.value)}
              >
                <option>UTC</option>
                <option>Asia/Kolkata</option>
                <option>Europe/London</option>
                <option>America/New_York</option>
              </select>
            </label>
          )}
        </div>
        <div className="publish-note">
          <b>Plan first, apply second</b>
          <span>
            The immutable agent version, manifest, frequency, and bounded Brain
            policy are stored in PostgreSQL for review and durable
            orchestration.
          </span>
        </div>
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy || !agent || !environment}>
            {busy ? "Generating…" : "Generate deployment plan"}
          </button>
        </div>
      </form>
    </div>
  );
}
function RegistryPage({
  registries,
  reload,
  onNotice,
}: {
  registries: Registry[];
  reload: () => void;
  onNotice: (v: string) => void;
}) {
  const [type, setType] = React.useState("ALL");
  const [adding, setAdding] = React.useState(false);
  const [selected, setSelected] = React.useState("");
  const [artifacts, setArtifacts] = React.useState<Artifact[]>([]);
  const [busy, setBusy] = React.useState("");
  const rows = registries.filter(
    (r) => type === "ALL" || r.registryType === type,
  );
  async function sync(r: Registry) {
    setBusy(r.registryId);
    try {
      const result = await api<{ discovered: number }>(
        `/api/v1/registries/${r.registryId}/sync`,
        { method: "POST" },
      );
      setSelected(r.registryId);
      setArtifacts(await api(`/api/v1/registries/${r.registryId}/artifacts`));
      await reload();
      onNotice(
        `Synchronized ${result.discovered} ${r.registryType.toLowerCase()} artifact(s) across the catalog.`,
      );
    } finally {
      setBusy("");
    }
  }
  async function show(r: Registry) {
    setSelected(r.registryId);
    setArtifacts(await api(`/api/v1/registries/${r.registryId}/artifacts`));
  }
  async function pull(a: Artifact) {
    setBusy(a.id);
    try {
      await api(`/api/v1/registries/${selected}/artifacts/${a.id}/pull`, {
        method: "POST",
      });
      setArtifacts(await api(`/api/v1/registries/${selected}/artifacts`));
      await reload();
      onNotice(
        `Pulled ${a.name} ${a.version} and refreshed dependent catalogs.`,
      );
    } finally {
      setBusy("");
    }
  }
  async function removeRegistry(r: Registry) {
    if (
      !window.confirm(
        `Delete registry ${r.displayName} and its discovered artifacts?`,
      )
    )
      return;
    await api(`/api/v1/registries/${r.registryId}`, { method: "DELETE" });
    if (selected === r.registryId) {
      setSelected("");
      setArtifacts([]);
    }
    await reload();
    onNotice(`${r.displayName} deleted.`);
  }
  async function removeArtifact(a: Artifact) {
    if (!window.confirm(`Delete discovered artifact ${a.name}?`)) return;
    await api(`/api/v1/registries/${selected}/artifacts/${a.id}`, {
      method: "DELETE",
    });
    setArtifacts(await api(`/api/v1/registries/${selected}/artifacts`));
    await reload();
    onNotice(`${a.name} removed from the synchronized catalog.`);
  }
  return (
    <>
      <section className="panel">
        <div className="panel-title">
          <h2>Repository registries</h2>
          <div className="actions">
            <select
              aria-label="Registry type"
              value={type}
              onChange={(e) => setType(e.target.value)}
            >
              <option>ALL</option>
              <option>AGENT</option>
              <option>MCP</option>
              <option>SKILL</option>
            </select>
            <button className="primary" onClick={() => setAdding(true)}>
              Connect repository
            </button>
          </div>
        </div>
        <div className="registry-grid">
          {rows.map((r) => (
            <article
              className={`registry ${selected === r.registryId ? "selected" : ""}`}
              key={r.registryId}
            >
              <div>
                <span className={`kind ${r.registryType.toLowerCase()}`}>
                  {r.registryType}
                </span>
                <span
                  className={`status ${(r.syncStatus || r.status).toLowerCase()}`}
                >
                  {r.syncStatus || r.status}
                </span>
              </div>
              <h3>{r.displayName}</h3>
              <a href={r.sourceUri} target="_blank" rel="noreferrer">
                {r.sourceUri}
              </a>
              <p>
                Branch: {String(r.metadata.branch || "main")} ·{" "}
                {r.lastSyncedAt
                  ? `Synced ${new Date(r.lastSyncedAt).toLocaleString()}`
                  : "Not synchronized"}
              </p>
              {r.syncError && <small>{r.syncError}</small>}
              <footer>
                <button onClick={() => removeRegistry(r)}>Delete</button>
                <button onClick={() => show(r)}>Artifacts</button>
                <button
                  className="primary"
                  disabled={busy === r.registryId}
                  onClick={() => sync(r)}
                >
                  {busy === r.registryId ? "Syncing…" : "Sync"}
                </button>
              </footer>
            </article>
          ))}
        </div>
      </section>
      {selected && (
        <section className="panel">
          <div className="panel-title">
            <h2>Discovered artifacts</h2>
            <span>{artifacts.length} entries</span>
          </div>
          {artifacts.length === 0 ? (
            <Empty title="Sync this registry to discover artifacts" />
          ) : (
            <div className="artifact-list">
              {artifacts.map((a) => (
                <article key={`${a.id}-${a.version}`}>
                  <div>
                    <span className={`kind ${a.type.toLowerCase()}`}>
                      {a.type}
                    </span>
                    <h3>
                      {a.name} <small>{a.version}</small>
                    </h3>
                    <p>{a.description}</p>
                    <code>{a.path}</code>
                  </div>
                  <div className="actions">
                    <button onClick={() => removeArtifact(a)}>Delete</button>
                    <button
                      className={a.state === "PULLED" ? "" : "primary"}
                      disabled={busy === a.id}
                      onClick={() => pull(a)}
                    >
                      {busy === a.id
                        ? "Pulling…"
                        : a.state === "PULLED"
                          ? "Pull again"
                          : "Pull"}
                    </button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      )}
      {adding && (
        <RegistryModal
          close={() => setAdding(false)}
          saved={() => {
            setAdding(false);
            onNotice("Repository registry connected.");
            reload();
          }}
        />
      )}
    </>
  );
}
function RegistryModal({
  close,
  saved,
}: {
  close: () => void;
  saved: () => void;
}) {
  const [url, setUrl] = React.useState(
    "https://github.com/ravikumar10/agent-studio-sample-registry",
  );
  const [type, setType] = React.useState<"AGENT" | "MCP" | "SKILL">("AGENT");
  const [name, setName] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const parts = url.replace(/\/$/, "").split("/");
      const owner = parts.at(-2) || "";
      const repo = parts.at(-1) || "";
      await api("/api/v1/registries", {
        method: "POST",
        body: JSON.stringify({
          registryId: `${owner}-${repo}-${type.toLowerCase()}`,
          registryType: type,
          displayName: name || `${repo} ${type.toLowerCase()}`,
          sourceUri: url,
          owner,
          metadata: {
            branch: "main",
            manifest: "catalog.json",
            syncMode: "MANUAL",
          },
        }),
      });
      saved();
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="backdrop">
      <form className="modal" onSubmit={submit}>
        <div className="panel-title">
          <h2>Connect GitHub repository</h2>
          <button type="button" onClick={close}>
            ×
          </button>
        </div>
        <label>
          Repository URL
          <input
            required
            type="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
          />
        </label>
        <label>
          Artifact type
          <select
            value={type}
            onChange={(e) => setType(e.target.value as typeof type)}
          >
            <option>AGENT</option>
            <option>MCP</option>
            <option>SKILL</option>
          </select>
        </label>
        <label>
          Display name
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Optional"
          />
        </label>
        <p>
          Connect the same repository once per artifact type. Sync discovers
          metadata; pulling content requires an explicit action.
        </p>
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Connecting…" : "Connect"}
          </button>
        </div>
      </form>
    </div>
  );
}
function MemoryPage({ onNotice }: { onNotice: (v: string) => void }) {
  const [ns, setNs] = React.useState("agent-session");
  const [key, setKey] = React.useState("demo");
  const [tier, setTier] = React.useState("BOTH");
  const [value, setValue] = React.useState(
    '{"preference":"concise","project":"agent-studio"}',
  );
  const [result, setResult] = React.useState("");
  async function save() {
    try {
      const content = JSON.parse(value);
      await api(
        `/api/v1/memory/${encodeURIComponent(ns)}/${encodeURIComponent(key)}`,
        {
          method: "PUT",
          body: JSON.stringify({
            tier,
            content,
            ttlSeconds: 3600,
            classification: "INTERNAL",
          }),
        },
      );
      onNotice(`Saved ${key} to ${tier.toLowerCase()} memory.`);
    } catch (e) {
      setResult(String(e));
    }
  }
  async function read() {
    try {
      setResult(
        JSON.stringify(
          await api(
            `/api/v1/memory/${encodeURIComponent(ns)}/${encodeURIComponent(key)}`,
          ),
          null,
          2,
        ),
      );
    } catch (e) {
      setResult(String(e));
    }
  }
  async function remove() {
    if (
      !window.confirm(`Delete memory ${ns}/${key} from hot and cold storage?`)
    )
      return;
    await api(
      `/api/v1/memory/${encodeURIComponent(ns)}/${encodeURIComponent(key)}`,
      { method: "DELETE" },
    );
    setResult("");
    onNotice(`Deleted ${ns}/${key} from all memory tiers.`);
  }
  return (
    <section className="panel memory">
      <h2>Memory explorer</h2>
      <div className="form-grid">
        <label>
          Namespace
          <input value={ns} onChange={(e) => setNs(e.target.value)} />
        </label>
        <label>
          Key
          <input value={key} onChange={(e) => setKey(e.target.value)} />
        </label>
        <label>
          Tier
          <select value={tier} onChange={(e) => setTier(e.target.value)}>
            <option>HOT</option>
            <option>COLD</option>
            <option>BOTH</option>
          </select>
        </label>
      </div>
      <label>
        JSON content
        <textarea value={value} onChange={(e) => setValue(e.target.value)} />
      </label>
      <div className="actions">
        <button className="primary" onClick={save}>
          Save memory
        </button>
        <button onClick={read}>Read memory</button>
        <button onClick={remove}>Delete memory</button>
      </div>
      {result && <pre>{result}</pre>}
      <div className="memory-help">
        <article>
          <b>Hot memory</b>
          <span>Redis · TTL · low latency · working/session context</span>
        </article>
        <article>
          <b>Tool-result cache</b>
          <span>
            Redis · tenant-isolated exact fingerprints · suppresses repeated
            read-only calls
          </span>
        </article>
        <article>
          <b>Cold memory</b>
          <span>PostgreSQL · durable · governed · long-term knowledge</span>
        </article>
        <article>
          <b>Semantic knowledge</b>
          <span>
            Optional Redis Vector · embeddings · similarity retrieval, kept
            separate from exact tool caching
          </span>
        </article>
      </div>
    </section>
  );
}
function RunsPage({
  runs,
  streamState,
}: {
  runs: Run[];
  streamState: "connecting" | "live" | "retrying";
}) {
  return (
    <section className="panel">
      <div className="panel-title">
        <h2>Recent runs</h2>
        <span className={`stream-state ${streamState}`}>
          <i />
          {streamState === "live"
            ? "Live updates"
            : streamState === "retrying"
              ? "Reconnecting…"
              : "Connecting…"}
        </span>
      </div>
      {runs.length === 0 ? (
        <Empty title="No runs yet" />
      ) : (
        <table>
          <thead>
            <tr>
              <th>Run</th>
              <th>Agent version</th>
              <th>Status</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {runs.map((r) => (
              <tr key={r.runId}>
                <td>
                  <code>{r.runId.slice(0, 8)}</code>
                </td>
                <td>
                  {r.agentId} · {r.agentVersion}
                </td>
                <td>
                  <span className={`status ${r.status.toLowerCase()}`}>
                    {r.status}
                  </span>
                </td>
                <td>{new Date(r.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
function CatalogPage({ endpoint }: { endpoint: string }) {
  const [items, setItems] = React.useState<Record<string, unknown>[]>([]);
  React.useEffect(() => {
    api<Record<string, unknown>[]>(`/api/v1/${endpoint}`)
      .then(setItems)
      .catch(() => setItems([]));
  }, [endpoint]);
  return (
    <section className="panel">
      <h2>
        {endpoint === "capabilities"
          ? "Capability catalog"
          : "Model profile catalog"}
      </h2>
      {items.length === 0 ? (
        <Empty title="Catalog is empty" />
      ) : (
        <pre>{JSON.stringify(items, null, 2)}</pre>
      )}
    </section>
  );
}
function CapabilityProvidersPage({
  capabilities,
  onNotice,
}: {
  capabilities: Capability[];
  onNotice: (v: string) => void;
}) {
  const [providers, setProviders] = React.useState<CapabilityProvider[]>([]);
  const [bindings, setBindings] = React.useState<CapabilityBinding[]>([]);
  const [adding, setAdding] = React.useState(false);
  const [binding, setBinding] = React.useState("");
  const [error, setError] = React.useState("");
  const refresh = React.useCallback(async () => {
    try {
      setError("");
      const [p, b] = await Promise.all([
        api<CapabilityProvider[]>("/api/v1/capability-providers"),
        api<CapabilityBinding[]>("/api/v1/capability-providers/bindings"),
      ]);
      setProviders(p);
      setBindings(b);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);
  React.useEffect(() => {
    refresh();
  }, [refresh]);
  async function verify(id: string) {
    try {
      const r = await api<{ valid: boolean; message: string }>(
        `/api/v1/capability-providers/${id}/verify`,
        { method: "POST" },
      );
      await refresh();
      onNotice(r.message);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }
  async function remove(id: string) {
    if (
      !window.confirm(`Delete provider ${id} and all its capability bindings?`)
    )
      return;
    await api(`/api/v1/capability-providers/${id}`, { method: "DELETE" });
    await refresh();
    onNotice("Provider and its bindings deleted.");
  }
  async function addBinding(providerId: string) {
    if (!binding) return;
    const operation = window.prompt(
      `Remote operation or HTTP path for ${binding}`,
      binding.endsWith(".describe-schema")
        ? "/tools/database.describe-schema"
        : binding.endsWith(".query-readonly")
          ? "/tools/database.query-readonly"
          : `/tools/${binding}`,
    );
    if (!operation) return;
    await api(`/api/v1/capability-providers/${providerId}/bindings`, {
      method: "POST",
      body: JSON.stringify({
        capabilityId: binding,
        remoteOperationName: operation,
        providerVersion: "1.0.0",
        routingWeight: 100,
        compatibility: {},
      }),
    });
    setBinding("");
    await refresh();
    onNotice(
      "Capability bound. Agents selecting it will resolve this provider automatically.",
    );
  }
  return (
    <>
      <section className="stats">
        <article>
          <strong>{capabilities.length}</strong>
          <span>Logical capabilities</span>
        </article>
        <article>
          <strong>{providers.length}</strong>
          <span>Configured MCP/API providers</span>
        </article>
        <article>
          <strong>{bindings.length}</strong>
          <span>Runtime bindings</span>
        </article>
      </section>
      {error && (
        <div className="notice error">
          {error}
          <button onClick={refresh}>Retry</button>
        </div>
      )}
      <section className="panel">
        <div className="panel-title">
          <div>
            <h2>MCP and API provider configuration</h2>
            <p className="muted">
              Agents bind logical capabilities; runtime resolves endpoint,
              operation, transport, and authentication automatically.
            </p>
          </div>
          <button className="primary" onClick={() => setAdding(true)}>
            Add provider
          </button>
        </div>
        <div className="grid">
          {providers.map((p) => (
            <article className="card" key={p.providerId}>
              <span
                className={`status ${p.healthStatus === "HEALTHY" ? "active" : ""}`}
              >
                {p.healthStatus}
              </span>
              <h3>{p.displayName}</h3>
              <code>{p.providerId}</code>
              <p>
                {p.integrationKind} · {p.providerType} · {p.transport} · {p.environment}
                <br />
                {p.endpointRef || "In-process adapter"}
                <br />
                {p.authType !== "NONE"
                  ? `${p.authType} · ${p.secretRef}`
                  : "No authentication"}
              </p>
              {Object.keys(p.credentials || {}).length > 0 && (
                <p className="credential-summary">
                  Encrypted fields: {Object.keys(p.credentials).join(", ")}
                </p>
              )}
              <div className="provider-bindings">
                {bindings
                  .filter((b) => b.providerId === p.providerId)
                  .map((b) => (
                    <span key={b.capabilityId}>
                      {b.capabilityId} → {b.remoteOperationName}
                    </span>
                  ))}
              </div>
              <label>
                Bind capability
                <select
                  value={binding}
                  onChange={(e) => setBinding(e.target.value)}
                >
                  <option value="">Select capability…</option>
                  {capabilities.map((c) => (
                    <option key={c.capabilityId} value={c.capabilityId}>
                      {c.displayName || c.capabilityId}
                    </option>
                  ))}
                </select>
              </label>
              <div className="card-actions">
                <button onClick={() => remove(p.providerId)}>Delete</button>
                <button onClick={() => verify(p.providerId)}>
                  Check connection
                </button>
                <button
                  className="primary"
                  disabled={!binding}
                  onClick={() => addBinding(p.providerId)}
                >
                  Bind
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>
      {adding && (
        <ProviderModal
          close={() => setAdding(false)}
          saved={() => {
            setAdding(false);
            refresh();
            onNotice(
              "Provider configuration saved. Verify it, then bind its capabilities.",
            );
          }}
        />
      )}
    </>
  );
}
function ProviderModal({
  close,
  saved,
}: {
  close: () => void;
  saved: () => void;
}) {
  const [id, setId] = React.useState("");
  const [name, setName] = React.useState("");
  const [types, setTypes] = React.useState<IntegrationType[]>([]);
  const [kind, setKind] = React.useState("POSTGRESQL");
  const [endpoint, setEndpoint] = React.useState("");
  const [configuration, setConfiguration] = React.useState<Record<string, string>>({});
  const [credentials, setCredentials] = React.useState<Record<string, string>>({});
  const [busy, setBusy] = React.useState(false);
  const selected = types.find((item) => item.kind === kind);
  React.useEffect(() => {
    api<IntegrationType[]>("/api/v1/integration-types").then((result) => {
      setTypes(result);
      const initial = result.find((item) => item.kind === "POSTGRESQL") || result[0];
      if (initial) selectType(initial, result);
    });
  }, []);
  function selectType(value: IntegrationType, available = types) {
    setKind(value.kind);
    setEndpoint("");
    setCredentials({});
    setConfiguration(
      Object.fromEntries(
        value.fields
          .filter((field) => field.defaultValue != null)
          .map((field) => [field.key, field.defaultValue || ""]),
      ),
    );
    if (!available.some((item) => item.kind === value.kind)) setTypes(available);
  }
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api("/api/v1/capability-providers", {
        method: "POST",
        body: JSON.stringify({
          providerId: id,
          displayName: name,
          integrationKind: kind,
          endpointRef: selected?.providerType === "IN_PROCESS" ? null : endpoint,
          authType: "NONE",
          configuration,
          credentials,
          environment: "local",
        }),
      });
      saved();
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="backdrop">
      <form className="modal" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">RUNTIME PROVIDER</p>
            <h2>Configure integration</h2>
          </div>
          <button type="button" onClick={close}>
            ×
          </button>
        </div>
        <label>
          Provider ID
          <input
            required
            pattern="[a-z0-9][a-z0-9.-]{2,191}"
            value={id}
            onChange={(e) => setId(e.target.value)}
          />
        </label>
        <label>
          Display name
          <input
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label>
          Integration type
          <select value={kind} onChange={(e) => {
            const next = types.find((item) => item.kind === e.target.value);
            if (next) selectType(next);
          }}>
            {types.map((item) => <option key={item.kind} value={item.kind}>{item.category} · {item.displayName}</option>)}
          </select>
        </label>
        {selected && <p className="integration-runtime">Runtime: {selected.providerType} · {selected.transport} · schema v{selected.schemaVersion}</p>}
        {selected?.providerType !== "IN_PROCESS" && (
          <label>
            Runtime adapter URL
            <input required type="url" value={endpoint} onChange={(e) => setEndpoint(e.target.value)} placeholder="https://tool-gateway.example.com" />
            <small>The agent calls this governed adapter; service credentials remain in the encrypted profile below.</small>
          </label>
        )}
        <div className="typed-fields">
          {selected?.fields.map((field) => (
            <label key={field.key}>
              {field.label}
              {field.inputType === "SELECT" ? (
                <select required={field.required} value={configuration[field.key] || ""} onChange={(e) => setConfiguration({...configuration,[field.key]:e.target.value})}>
                  <option value="">Select…</option>
                  {field.options.map((option) => <option key={option}>{option}</option>)}
                </select>
              ) : field.inputType === "BOOLEAN" ? (
                <select required={field.required} value={configuration[field.key] || ""} onChange={(e) => setConfiguration({...configuration,[field.key]:e.target.value})}><option value="">Select…</option><option value="true">Yes</option><option value="false">No</option></select>
              ) : (
                <input required={field.required} type={field.inputType === "URL" ? "url" : field.inputType === "NUMBER" ? "number" : "text"} placeholder={field.placeholder} value={configuration[field.key] || ""} onChange={(e) => setConfiguration({...configuration,[field.key]:e.target.value})} />
              )}
            </label>
          ))}
        </div>
        {selected && selected.credentialFields.length > 0 && <fieldset className="secret-fields"><legend>Encrypted credentials</legend><div className="typed-fields">
          {selected.credentialFields.map((field) => <label key={field.key}>{field.label}<input required={field.required} type="password" autoComplete="new-password" placeholder={field.placeholder} value={credentials[field.key] || ""} onChange={(e) => setCredentials({...credentials,[field.key]:e.target.value})} /></label>)}
        </div><small>Each credential is encrypted independently and is never returned by the API or stored in an agent version.</small></fieldset>}
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Saving…" : "Save provider"}
          </button>
        </div>
      </form>
    </div>
  );
}
function UserProfilePage({
  profiles,
  onNotice,
  onChanged,
}: {
  profiles: ModelProfile[];
  onNotice: (v: string) => void;
  onChanged: () => void;
}) {
  const [profile, setProfile] = React.useState<UserProfile | null>(null);
  const [name, setName] = React.useState("");
  const [email, setEmail] = React.useState("");
  const [theme, setTheme] = React.useState("system");
  const [defaultModel, setDefaultModel] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => {
    api<UserProfile>("/api/v1/user-profile").then((p) => {
      setProfile(p);
      setName(p.displayName);
      setEmail(p.email || "");
      const prefs = p.preferences || {};
      setTheme(String(prefs.theme || "system"));
      const preferred = String(prefs.defaultModelProfile || "");
      setDefaultModel(
        profiles.some((item) => item.profileId === preferred)
          ? preferred
          : profiles[0]?.profileId || "",
      );
    });
  }, [profiles]);
  async function save(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const saved = await api<UserProfile>("/api/v1/user-profile", {
        method: "PUT",
        body: JSON.stringify({
          displayName: name,
          email: email || null,
          preferences: {
            theme,
            ...(defaultModel ? { defaultModelProfile: defaultModel } : {}),
          },
        }),
      });
      setProfile(saved);
      await api("/api/v1/user-profile/configurations/agent-builder/defaults", {
        method: "PUT",
        body: JSON.stringify({ defaultModelProfile: defaultModel || null }),
      });
      await onChanged();
      onNotice("User profile saved and synchronized with Agent Builder.");
    } finally {
      setBusy(false);
    }
  }
  async function reset() {
    if (!window.confirm("Delete your Agent Builder defaults?")) return;
    await api("/api/v1/user-profile/configurations/agent-builder/defaults", {
      method: "DELETE",
    });
    setDefaultModel("");
    const saved = await api<UserProfile>("/api/v1/user-profile", {
      method: "PUT",
      body: JSON.stringify({
        displayName: name,
        email: email || null,
        preferences: { theme },
      }),
    });
    setProfile(saved);
    await onChanged();
    onNotice("Agent Builder defaults reset.");
  }
  return (
    <section className="panel">
      <div className="panel-title">
        <div>
          <h2>User profile</h2>
          <p className="muted">
            Configuration is scoped to {tenant} / {userId}. Secrets are managed
            separately and never returned here.
          </p>
        </div>
        <span className="status active">{profile ? "ACTIVE" : "LOADING"}</span>
      </div>
      <form onSubmit={save}>
        <div className="form-grid">
          <label>
            User ID
            <input disabled value={userId} />
          </label>
          <label>
            Display name
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
        </div>
        <div className="form-grid">
          <label>
            Theme
            <select value={theme} onChange={(e) => setTheme(e.target.value)}>
              <option value="system">System</option>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </label>
          <label>
            Default model profile
            <select
              value={defaultModel}
              onChange={(e) => setDefaultModel(e.target.value)}
            >
              <option value="">No default model</option>
              {profiles.map((item) => (
                <option key={item.profileId} value={item.profileId}>
                  {item.profileId}
                  {item.modelId ? ` · ${item.modelId}` : ""}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="publish-note">
          <b>Persisted user configuration</b>
          <span>
            Agent definitions and immutable versions remain in their dedicated
            tables; personal defaults live in user configurations.
          </span>
        </div>
        <div className="actions">
          <button type="button" onClick={reset}>
            Delete defaults
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Saving…" : "Save profile"}
          </button>
        </div>
      </form>
    </section>
  );
}
function ModelProfilesPage({
  profiles,
  reload,
  onNotice,
}: {
  profiles: ModelProfile[];
  reload: () => void | Promise<void>;
  onNotice: (v: string) => void;
}) {
  const [connections, setConnections] = React.useState<ModelConnection[]>([]);
  const [showConnection, setShowConnection] = React.useState(false);
  const [showProfile, setShowProfile] = React.useState(false);
  const [profileConnection, setProfileConnection] = React.useState("");
  const refresh = React.useCallback(
    () =>
      api<ModelConnection[]>("/api/v1/model-connections").then(setConnections),
    [],
  );
  React.useEffect(() => {
    refresh().catch(() => setConnections([]));
  }, [refresh]);
  async function removeConnection(c: ModelConnection) {
    if (
      !window.confirm(`Delete ${c.displayName} and its encrypted credential?`)
    )
      return;
    await api(`/api/v1/model-connections/${c.connectionId}`, {
      method: "DELETE",
    });
    await refresh();
    await reload();
    onNotice(`${c.displayName} deleted.`);
  }
  async function removeProfile(p: ModelProfile) {
    if (!window.confirm(`Delete model profile ${p.profileId}?`)) return;
    await api(`/api/v1/model-profiles/${p.profileId}`, { method: "DELETE" });
    await reload();
    onNotice(`${p.profileId} deleted.`);
  }
  function createProfile(connectionId = "") {
    setProfileConnection(connectionId);
    setShowProfile(true);
  }
  return (
    <>
      <section className="panel">
        <div className="panel-title">
          <div>
            <h2>Model connections</h2>
            <p className="muted">
              API keys are encrypted at rest. A connection discovers provider
              models; create a logical profile before assigning one to an agent.
            </p>
          </div>
          <button className="primary" onClick={() => setShowConnection(true)}>
            Onboard provider
          </button>
        </div>
        <div className="model-grid">
          {connections.map((c) => (
            <article key={c.connectionId}>
              <span className="kind model">{c.provider.replace("_", " ")}</span>
              <h3>{c.displayName}</h3>
              <code>{c.connectionId}</code>
              <p>{c.baseUrl}</p>
              <small>
                {c.secretRef} · {c.enabled ? "enabled" : "disabled"}
              </small>
              <div className="actions">
                <button onClick={() => removeConnection(c)}>Delete</button>
                <button
                  className="primary"
                  onClick={() => createProfile(c.connectionId)}
                >
                  Create model profile
                </button>
              </div>
            </article>
          ))}
          {connections.length === 0 && (
            <Empty title="No model provider connected" />
          )}
        </div>
      </section>
      <section className="panel">
        <div className="panel-title">
          <h2>Logical model profiles</h2>
          <button
            disabled={!connections.length}
            onClick={() => createProfile()}
          >
            Create profile
          </button>
        </div>
        <div className="model-grid">
          {profiles.map((p) => (
            <article key={p.profileId}>
              <span className="kind model">{p.qualityTier || "LOGICAL"}</span>
              <h3>{p.profileId}</h3>
              <p>{p.modelId || "Provider-selected model"}</p>
              <small>
                {p.connectionId || "No provider pinned"} ·{" "}
                {(p.requiredFeatures || []).join(", ") || "text"}
              </small>
              <div className="actions">
                <button onClick={() => removeProfile(p)}>Delete</button>
              </div>
            </article>
          ))}
        </div>
      </section>
      {showConnection && (
        <SecureModelConnectionModal
          close={() => setShowConnection(false)}
          saved={async (connectionId) => {
            setShowConnection(false);
            await refresh();
            await reload();
            setProfileConnection(connectionId);
            setShowProfile(true);
            onNotice(
              "Provider connected. Select a discovered model to finish its logical profile.",
            );
          }}
        />
      )}
      {showProfile && (
        <ModelProfileModal
          connections={connections}
          initialConnectionId={profileConnection}
          close={() => setShowProfile(false)}
          saved={async () => {
            setShowProfile(false);
            setProfileConnection("");
            await reload();
            onNotice(
              "Logical model profile created and available to Agent Builder.",
            );
          }}
        />
      )}
    </>
  );
}
function SecureModelConnectionModal({
  close,
  saved,
}: {
  close: () => void;
  saved: (connectionId: string) => void;
}) {
  const [provider, setProvider] =
    React.useState<ModelConnection["provider"]>("OPENAI");
  const [id, setId] = React.useState("openai-primary");
  const [name, setName] = React.useState("OpenAI primary");
  const [baseUrl, setBaseUrl] = React.useState("https://api.openai.com/v1");
  const [apiKey, setApiKey] = React.useState("");
  const [organizationId, setOrganizationId] = React.useState("");
  const [projectId, setProjectId] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [failure, setFailure] = React.useState("");
  const [verification, setVerification] = React.useState<{
    state: "idle" | "checking" | "valid" | "invalid" | "unsupported";
    message: string;
    fingerprint: string;
  }>({
    state: "idle",
    message: "Enter an API key to verify it from the backend.",
    fingerprint: "",
  });
  const fingerprint = JSON.stringify({
    provider,
    baseUrl,
    apiKey,
    organizationId,
    projectId,
  });
  function changeProvider(value: ModelConnection["provider"]) {
    setProvider(value);
    if (value === "OPENAI") setBaseUrl("https://api.openai.com/v1");
    else if (value === "ANTHROPIC") setBaseUrl("https://api.anthropic.com/v1");
  }
  React.useEffect(() => {
    if (provider === "OPENAI_COMPATIBLE") {
      setVerification({
        state: "unsupported",
        message:
          "Automatic verification for custom compatible endpoints will be added later.",
        fingerprint: "",
      });
      return;
    }
    if (apiKey.trim().length < 8 || !baseUrl.startsWith("https://")) {
      setVerification({
        state: "idle",
        message: "Enter a complete API key and HTTPS base URL to verify.",
        fingerprint: "",
      });
      return;
    }
    let active = true;
    const expected = fingerprint;
    setVerification({
      state: "checking",
      message: "Checking API key with the provider…",
      fingerprint: "",
    });
    const timer = window.setTimeout(() => {
      api<{
        supported: boolean;
        valid: boolean;
        modelCount: number;
        message: string;
      }>("/api/v1/model-connections/verify", {
        method: "POST",
        body: JSON.stringify({
          provider,
          baseUrl,
          apiKey,
          organizationId: organizationId || null,
          projectId: projectId || null,
        }),
      })
        .then((result) => {
          if (active)
            setVerification({
              state: result.valid
                ? "valid"
                : result.supported
                  ? "invalid"
                  : "unsupported",
              message: result.message,
              fingerprint: result.valid ? expected : "",
            });
        })
        .catch((e) => {
          if (active)
            setVerification({
              state: "invalid",
              message: e instanceof Error ? e.message : String(e),
              fingerprint: "",
            });
        });
    }, 650);
    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [provider, baseUrl, apiKey, organizationId, projectId]);
  const verified =
    verification.state === "valid" && verification.fingerprint === fingerprint;
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!verified) return;
    setBusy(true);
    setFailure("");
    try {
      await api("/api/v1/model-connections", {
        method: "POST",
        body: JSON.stringify({
          connectionId: id,
          displayName: name,
          provider,
          baseUrl,
          apiKey,
          organizationId: organizationId || null,
          projectId: projectId || null,
        }),
      });
      setApiKey("");
      saved(id);
    } catch (e) {
      setFailure(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="backdrop">
      <form className="modal provider-modal" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">MODEL GATEWAY</p>
            <h2>Onboard provider</h2>
          </div>
          <button type="button" onClick={close}>
            ×
          </button>
        </div>
        <label>
          Provider
          <select
            value={provider}
            onChange={(e) =>
              changeProvider(e.target.value as ModelConnection["provider"])
            }
          >
            <option value="OPENAI">OpenAI</option>
            <option value="ANTHROPIC">Anthropic / Claude</option>
            <option value="OPENAI_COMPATIBLE">
              OpenAI-compatible endpoint
            </option>
          </select>
        </label>
        <div className="form-grid">
          <label>
            Connection ID
            <input
              required
              value={id}
              onChange={(e) => setId(e.target.value)}
            />
          </label>
          <label>
            Display name
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
        </div>
        <label>
          Base URL
          <input
            required
            type="url"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
          />
        </label>
        <label>
          API key
          <input
            required
            type="password"
            autoComplete="new-password"
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="Paste API key"
          />
          <small>
            Sent only to this backend for verification, then encrypted with
            AES-GCM when saved.
          </small>
        </label>
        {provider === "OPENAI" && (
          <div className="form-grid">
            <label>
              Organization ID
              <input
                value={organizationId}
                onChange={(e) => setOrganizationId(e.target.value)}
                placeholder="Optional"
              />
            </label>
            <label>
              Project ID
              <input
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                placeholder="Optional"
              />
            </label>
          </div>
        )}
        <div
          className={`notice ${verification.state === "valid" ? "success" : verification.state === "invalid" ? "error" : ""}`}
        >
          <b>
            {verification.state === "checking"
              ? "VERIFYING"
              : verification.state === "valid"
                ? "CONNECTED"
                : verification.state === "invalid"
                  ? "REJECTED"
                  : verification.state === "unsupported"
                    ? "NOT SUPPORTED"
                    : "NOT VERIFIED"}
          </b>{" "}
          · {verification.message}
        </div>
        {failure && <div className="notice error">{failure}</div>}
        <div className="publish-note">
          <b>Backend-verified onboarding</b>
          <span>
            Save is enabled only while the current provider, URL, key,
            organization and project match the successful verification.
          </span>
        </div>
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy || !verified}>
            {busy
              ? "Encrypting…"
              : verification.state === "checking"
                ? "Verifying…"
                : "Save and choose model"}
          </button>
        </div>
      </form>
    </div>
  );
}
function ModelConnectionModal({
  close,
  saved,
}: {
  close: () => void;
  saved: () => void;
}) {
  const [provider, setProvider] =
    React.useState<ModelConnection["provider"]>("OPENAI");
  const [id, setId] = React.useState("openai-primary");
  const [name, setName] = React.useState("OpenAI primary");
  const [baseUrl, setBaseUrl] = React.useState("https://api.openai.com/v1");
  const [secretRef, setSecretRef] = React.useState("env://OPENAI_API_KEY");
  const [organizationId, setOrganizationId] = React.useState("");
  const [projectId, setProjectId] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  function changeProvider(value: ModelConnection["provider"]) {
    setProvider(value);
    if (value === "OPENAI") {
      setBaseUrl("https://api.openai.com/v1");
      setSecretRef("env://OPENAI_API_KEY");
    } else if (value === "ANTHROPIC") {
      setBaseUrl("https://api.anthropic.com/v1");
      setSecretRef("env://ANTHROPIC_API_KEY");
    }
  }
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api("/api/v1/model-connections", {
        method: "POST",
        body: JSON.stringify({
          connectionId: id,
          displayName: name,
          provider,
          baseUrl,
          secretRef,
          organizationId: organizationId || null,
          projectId: projectId || null,
        }),
      });
      saved();
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="backdrop">
      <form className="modal provider-modal" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">MODEL GATEWAY</p>
            <h2>Onboard provider</h2>
          </div>
          <button type="button" onClick={close}>
            ×
          </button>
        </div>
        <label>
          Provider
          <select
            value={provider}
            onChange={(e) =>
              changeProvider(e.target.value as ModelConnection["provider"])
            }
          >
            <option value="OPENAI">OpenAI</option>
            <option value="ANTHROPIC">Anthropic / Claude</option>
            <option value="OPENAI_COMPATIBLE">
              OpenAI-compatible endpoint
            </option>
          </select>
        </label>
        <div className="form-grid">
          <label>
            Connection ID
            <input
              required
              value={id}
              onChange={(e) => setId(e.target.value)}
            />
          </label>
          <label>
            Display name
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
        </div>
        <label>
          Base URL
          <input
            required
            type="url"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
          />
        </label>
        <label>
          Credential reference
          <input
            required
            value={secretRef}
            onChange={(e) => setSecretRef(e.target.value)}
            placeholder="env://OPENAI_API_KEY"
          />
          <small>
            Use env://NAME or secret://namespace/name. Never paste an API key
            here.
          </small>
        </label>
        {provider === "OPENAI" && (
          <div className="form-grid">
            <label>
              Organization ID
              <input
                value={organizationId}
                onChange={(e) => setOrganizationId(e.target.value)}
                placeholder="Optional"
              />
            </label>
            <label>
              Project ID
              <input
                value={projectId}
                onChange={(e) => setProjectId(e.target.value)}
                placeholder="Optional"
              />
            </label>
          </div>
        )}
        <div className="publish-note">
          <b>Provider models are discovered at runtime</b>
          <span>
            OpenAI and Anthropic both expose model-list APIs. Store stable model
            IDs in profiles and rotate credentials outside Agent Studio.
          </span>
        </div>
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy ? "Saving…" : "Save connection"}
          </button>
        </div>
      </form>
    </div>
  );
}
function ModelProfileModal({
  connections,
  initialConnectionId,
  close,
  saved,
}: {
  connections: ModelConnection[];
  initialConnectionId?: string;
  close: () => void;
  saved: () => void;
}) {
  const [id, setId] = React.useState("");
  const [connectionId, setConnectionId] = React.useState(
    initialConnectionId || connections[0]?.connectionId || "",
  );
  const [modelId, setModelId] = React.useState("");
  const [models, setModels] = React.useState<ModelOption[]>([]);
  const [discovery, setDiscovery] = React.useState("Loading models…");
  const [quality, setQuality] = React.useState("BALANCED");
  const [features, setFeatures] = React.useState<string[]>([
    "TOOL_CALLING",
    "STRUCTURED_OUTPUT",
  ]);
  const [maxOutput, setMaxOutput] = React.useState(4096);
  const [busy, setBusy] = React.useState(false);
  const [failure, setFailure] = React.useState("");
  const toggle = (f: string) =>
    setFeatures((v) => (v.includes(f) ? v.filter((x) => x !== f) : [...v, f]));
  React.useEffect(() => {
    if (!connectionId) return;
    setDiscovery("Loading models…");
    setFailure("");
    api<ModelOption[]>(`/api/v1/model-connections/${connectionId}/models`)
      .then((items) => {
        setModels(items);
        setModelId("");
        setDiscovery(
          items.length
            ? `${items.length} provider models available`
            : "Enter a model ID for this compatible endpoint",
        );
      })
      .catch((e) => {
        setModels([]);
        setDiscovery("Model discovery unavailable");
        setFailure(e instanceof Error ? e.message : String(e));
      });
  }, [connectionId]);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFailure("");
    try {
      await api("/api/v1/model-profiles", {
        method: "POST",
        body: JSON.stringify({
          id,
          connectionId,
          modelId,
          qualityTier: quality,
          latencyTier: "NORMAL",
          maxInputTokens: 128000,
          maxOutputTokens: maxOutput,
          requiredFeatures: features,
          fallbackPolicy: "NONE",
          generationParameters: { temperature: 0.2 },
        }),
      });
      saved();
    } catch (e) {
      setFailure(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="backdrop">
      <form className="modal provider-modal" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">MODEL GATEWAY</p>
            <h2>Create model profile</h2>
          </div>
          <button type="button" onClick={close}>
            ×
          </button>
        </div>
        <div className="form-grid">
          <label>
            Profile ID
            <input
              required
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="reasoning-primary"
            />
          </label>
          <label>
            Connection
            <select
              required
              value={connectionId}
              onChange={(e) => setConnectionId(e.target.value)}
            >
              <option value="">Select a connection</option>
              {connections.map((c) => (
                <option key={c.connectionId} value={c.connectionId}>
                  {c.displayName}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label>
          Provider model
          {models.length ? (
            <select
              required
              value={modelId}
              onChange={(e) => setModelId(e.target.value)}
            >
              <option value="">Select a model</option>
              {models.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.displayName} · {m.id}
                </option>
              ))}
            </select>
          ) : (
            <input
              required
              value={modelId}
              onChange={(e) => setModelId(e.target.value)}
              placeholder="Provider model ID"
            />
          )}
          <small>{discovery}</small>
        </label>
        <div className="form-grid">
          <label>
            Quality tier
            <select
              value={quality}
              onChange={(e) => setQuality(e.target.value)}
            >
              <option>FAST</option>
              <option>BALANCED</option>
              <option>HIGH</option>
              <option>MAX</option>
            </select>
          </label>
          <label>
            Maximum output tokens
            <input
              type="number"
              min="1"
              value={maxOutput}
              onChange={(e) => setMaxOutput(Number(e.target.value))}
            />
          </label>
        </div>
        <fieldset>
          <legend>Required capabilities</legend>
          <div className="feature-row">
            {[
              "TOOL_CALLING",
              "STRUCTURED_OUTPUT",
              "VISION",
              "AUDIO",
              "LONG_CONTEXT",
            ].map((f) => (
              <label key={f}>
                <input
                  type="checkbox"
                  checked={features.includes(f)}
                  onChange={() => toggle(f)}
                />
                {f.replace("_", " ")}
              </label>
            ))}
          </div>
        </fieldset>
        {failure && <div className="notice error">{failure}</div>}
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button
            className="primary"
            disabled={busy || !connectionId || !modelId}
          >
            {busy ? "Creating…" : "Create profile"}
          </button>
        </div>
      </form>
    </div>
  );
}
function AgentModal({
  editing,
  agents,
  capabilities,
  availableSkills,
  profiles,
  defaultModel,
  close,
  saved,
}: {
  editing: Agent | null;
  agents: Agent[];
  capabilities: Capability[];
  availableSkills: Skill[];
  profiles: ModelProfile[];
  defaultModel: string;
  close: () => void;
  saved: (message: string) => void;
}) {
  const usableProfiles = profiles.filter((p) => !p.connectionId || !!p.modelId);
  const [mode, setMode] = React.useState<"compose" | "import">(
    editing?.tags?.includes("code-backed") ? "import" : "compose",
  );
  const [id, setId] = React.useState(editing?.id || "");
  const [name, setName] = React.useState(editing?.displayName || "");
  const [team, setTeam] = React.useState(editing?.ownerTeam || "platform");
  const [description, setDescription] = React.useState(
    editing?.description || "",
  );
  const [model, setModel] = React.useState(
    usableProfiles.some((p) => p.profileId === defaultModel)
      ? defaultModel
      : usableProfiles[0]?.profileId || "",
  );
  const [selectedSkills, setSelectedSkills] = React.useState<string[]>([]);
  const [selected, setSelected] = React.useState<string[]>([]);
  const [pipeline, setPipeline] = React.useState<string[]>([]);
  const [members, setMembers] = React.useState<string[]>([]);
  const [interaction, setInteraction] = React.useState<
    Agent["interactionMode"]
  >(editing?.interactionMode || "TASK_AND_CHAT");
  const [topology, setTopology] = React.useState<Agent["topology"]>(
    editing?.topology || "SINGLE_AGENT",
  );
  const [trigger, setTrigger] = React.useState<Agent["triggerMode"]>(
    editing?.triggerMode || "ON_DEMAND",
  );
  const [repo, setRepo] = React.useState("https://github.com/ravikumar10/");
  const [current, setCurrent] = React.useState<AgentVersion | null>(null);
  const [family, setFamily] = React.useState("ALL");
  const [busy, setBusy] = React.useState(false);
  const [providerBindings, setProviderBindings] = React.useState<
    CapabilityBinding[]
  >([]);
  const [capabilityProfiles, setCapabilityProfiles] = React.useState<
    Record<string, string>
  >({});
  const [placement, setPlacement] = React.useState(
    mode === "compose" ? "IN_PROCESS" : "DOCKER",
  );
  const [cron, setCron] = React.useState("0 */6 * * *");
  const [eventSource, setEventSource] = React.useState("");
  const modelMissing = !!model && !profiles.some((p) => p.profileId === model);
  React.useEffect(() => {
    api<CapabilityBinding[]>("/api/v1/capability-providers/bindings")
      .then(setProviderBindings)
      .catch(() => setProviderBindings([]));
  }, []);
  const toggle = (value: string) => {
    setSelected((values) =>
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
    setPipeline((values) =>
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  };
  const toggleMember = (value: string) =>
    setMembers((values) =>
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  const toggleSkill = (value: string) => {
    setSelectedSkills((values) =>
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
    setPipeline((values) =>
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  };
  const move = (index: number, delta: number) =>
    setPipeline((values) => {
      const target = index + delta;
      if (target < 0 || target >= values.length) return values;
      const next = [...values];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  React.useEffect(() => {
    if (!editing) return;
    api<AgentVersion[]>(`/api/v1/agents/${editing.id}/versions`)
      .then((versions) => {
        const v = versions[0];
        if (!v) return;
        setCurrent(v);
        setMode(v.runtimeType === "CONFIG" ? "compose" : "import");
        setModel(v.modelProfile || "");
        setSelected(v.toolCapabilitiesRequired || []);
        setPipeline(v.toolCapabilitiesRequired || []);
        setMembers(
          (v.agentCapabilitiesRequired || []).map((x) =>
            x.replace(/^agent\./, "").replace(/\.invoke$/, ""),
          ),
        );
        setRepo(
          v.artifactRef?.startsWith("http")
            ? v.artifactRef
            : "https://github.com/ravikumar10/",
        );
        api<AgentRuntimeConfig>(
          `/api/v1/agents/${editing.id}/versions/${v.version}/runtime-config`,
        )
          .then((c) => {
            setPlacement(c.executionPlacement);
            setCapabilityProfiles(c.capabilityProfiles || {});
            setCron(
              String(c.triggerConfiguration?.cronExpression || "0 */6 * * *"),
            );
            setEventSource(String(c.triggerConfiguration?.source || ""));
          })
          .catch(() => {});
        if (v.promptRef?.startsWith("plan://")) {
          try {
            const plan = JSON.parse(
              decodeURIComponent(v.promptRef.slice(7)),
            ) as { skills: string[]; order: string[] };
            setSelectedSkills(plan.skills || []);
            setPipeline(plan.order || v.toolCapabilitiesRequired || []);
          } catch {}
        } else if (v.promptRef?.startsWith("skills://")) {
          try {
            const refs = JSON.parse(decodeURIComponent(v.promptRef.slice(9)));
            setSelectedSkills(refs);
            setPipeline([...refs, ...(v.toolCapabilitiesRequired || [])]);
          } catch {
            setSelectedSkills([v.promptRef]);
          }
        } else if (v.promptRef) setSelectedSkills([v.promptRef]);
      })
      .catch(() => {});
  }, [editing]);
  const nextVersion = () => {
    if (!current) return "1.0.0";
    const parts = current.version.split(".").map(Number);
    return `${parts[0] || 1}.${parts[1] || 0}.${(parts[2] || 0) + 1}`;
  };
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      const metadata = {
        id,
        displayName: name,
        ownerTeam: team,
        description,
        interactionMode: interaction,
        topology,
        triggerMode: trigger,
        tags: [
          mode === "compose" ? "composed" : "code-backed",
          topology.toLowerCase(),
          interaction.toLowerCase(),
        ],
      };
      await api(editing ? `/api/v1/agents/${id}` : "/api/v1/agents", {
        method: editing ? "PUT" : "POST",
        body: JSON.stringify(metadata),
      });
      const versionNumber = nextVersion();
      const runtimeType =
        current?.runtimeType || (mode === "compose" ? "CONFIG" : "REMOTE_HTTP");
      const plan = {
        skills: selectedSkills,
        order: pipeline,
        llmPolicy: "ON_DEMAND",
        maxModelCalls: 2,
        reuseToolResults: true,
      };
      const version = {
        agentId: id,
        version: versionNumber,
        runtimeType,
        hostingMode:
          current?.hostingMode || (mode === "compose" ? "PLATFORM" : "CLIENT"),
        artifactRef:
          mode === "compose" ? `config://${id}/${versionNumber}` : repo,
        remoteEndpointRef: current?.remoteEndpointRef || null,
        capabilitiesProvided: [`agent.${id}.invoke`],
        toolCapabilitiesRequired: selected,
        agentCapabilitiesRequired: members.map(
          (member) => `agent.${member}.invoke`,
        ),
        modelProfile: model || null,
        promptRef: `plan://${encodeURIComponent(JSON.stringify(plan))}`,
        inputSchemaRef: `catalog://schemas/${id}-input`,
        outputSchemaRef: `catalog://schemas/${id}-output`,
        executionPolicyRef: "policy://bounded-llm-on-demand",
        securityPolicyRef: "policy://tenant-default",
        checksum: `ui-${Date.now()}`,
      };
      await api(`/api/v1/agents/${id}/versions`, {
        method: "POST",
        body: JSON.stringify(version),
      });
      const selectedProfiles = Object.fromEntries(
        Object.entries(capabilityProfiles).filter(
          ([capability, provider]) => selected.includes(capability) && provider,
        ),
      );
      const triggerConfiguration =
        trigger === "SCHEDULED"
          ? { cronExpression: cron, timeZone: "UTC" }
          : trigger === "EVENT_DRIVEN"
            ? { source: eventSource }
            : {};
      await api(
        `/api/v1/agents/${id}/versions/${versionNumber}/runtime-config`,
        {
          method: "PUT",
          body: JSON.stringify({
            executionPlacement: placement,
            triggerType: trigger,
            triggerConfiguration,
            resourceConfiguration:
              placement === "IN_PROCESS"
                ? { memory: "lightweight" }
                : { replicas: 1, cpu: "500m", memory: "512Mi" },
            capabilityProfiles: selectedProfiles,
          }),
        },
      );
      if (mode === "compose" || editing?.status === "ACTIVE") {
        await api(`/api/v1/agents/${id}/versions/${versionNumber}/validate`, {
          method: "POST",
        });
        await api(`/api/v1/agents/${id}/versions/${versionNumber}/activate`, {
          method: "POST",
        });
      }
      saved(
        editing
          ? `${name} updated and activated as version ${versionNumber}.`
          : `Agent ${versionNumber} created with named integrations and ${placement.toLowerCase()} placement.`,
      );
    } finally {
      setBusy(false);
    }
  }
  const visible = capabilities.filter(
    (c) => family === "ALL" || c.capabilityId.startsWith(`${family}.`),
  );
  return (
    <div
      className="backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.currentTarget === e.target) close();
      }}
    >
      <form className="modal builder" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">
              {editing ? "AGENT CONFIGURATION" : "AGENT BUILDER"}
            </p>
            <h2>
              {editing
                ? `Configure ${editing.displayName}`
                : mode === "compose"
                  ? "Compose an agent"
                  : "Import code agent"}
            </h2>
            {editing && (
              <small>
                Saving creates a new immutable version; running executions
                remain pinned.
              </small>
            )}
          </div>
          <button type="button" aria-label="Close" onClick={close}>
            ×
          </button>
        </div>
        {!editing && (
          <div className="mode-tabs">
            <button
              type="button"
              className={mode === "compose" ? "active" : ""}
              onClick={() => setMode("compose")}
            >
              Compose
            </button>
            <button
              type="button"
              className={mode === "import" ? "active" : ""}
              onClick={() => setMode("import")}
            >
              Import repository
            </button>
          </div>
        )}
        <div className="form-grid">
          <label>
            Agent ID
            <input
              disabled={!!editing}
              required
              pattern="[a-z0-9][a-z0-9.-]{2,127}"
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="text-to-sql"
            />
          </label>
          <label>
            Display name
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            Owner team
            <input
              required
              value={team}
              onChange={(e) => setTeam(e.target.value)}
            />
          </label>
        </div>
        <label>
          Description
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        <div className="form-grid contract-grid">
          <label>
            Interface
            <select
              value={interaction}
              onChange={(e) =>
                setInteraction(e.target.value as Agent["interactionMode"])
              }
            >
              <option value="TASK">Run once</option>
              <option value="CHAT">Chat</option>
              <option value="TASK_AND_CHAT">Run once + chat</option>
            </select>
          </label>
          <label>
            Topology
            <select
              value={topology}
              onChange={(e) => setTopology(e.target.value as Agent["topology"])}
            >
              <option value="SINGLE_AGENT">Single agent</option>
              <option value="MULTI_AGENT">Multi-agent system</option>
            </select>
          </label>
          <label>
            Trigger
            <select
              value={trigger}
              onChange={(e) =>
                setTrigger(e.target.value as Agent["triggerMode"])
              }
            >
              <option value="ON_DEMAND">On demand</option>
              <option value="SCHEDULED">Scheduled</option>
              <option value="EVENT_DRIVEN">Event driven</option>
            </select>
          </label>
        </div>
        {mode === "import" && (
          <label>
            Git repository URL
            <input
              required
              type="url"
              value={repo}
              onChange={(e) => setRepo(e.target.value)}
            />
            <small>
              Custom code runs out-of-process in an isolated worker/container.
            </small>
          </label>
        )}
        <label>
          Logical model profile
          <select value={model} onChange={(e) => setModel(e.target.value)}>
            {modelMissing && (
              <option value={model}>
                {model} · missing profile — choose a replacement
              </option>
            )}
            <option value="">No model</option>
            {usableProfiles.map((p) => (
              <option key={p.profileId} value={p.profileId}>
                {p.profileId}
                {p.modelId ? ` · ${p.modelId}` : ""}
              </option>
            ))}
          </select>
          {modelMissing && (
            <small className="error-text">
              This immutable agent version references a deleted profile. Choose
              a registered replacement or No model.
            </small>
          )}
        </label>
        <fieldset>
          <legend>Available skills</legend>
          <div className="capability-picker skill-picker">
            {availableSkills.map((skill) => (
              <label key={skill.skillId}>
                <input
                  type="checkbox"
                  checked={selectedSkills.includes(skill.reference)}
                  onChange={() => toggleSkill(skill.reference)}
                />
                <span>
                  <b>{skill.displayName}</b>
                  <small>{skill.description}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend>Database and MCP capabilities</legend>
          <div className="picker-toolbar">
            <label>
              Connector family
              <select
                value={family}
                onChange={(e) => setFamily(e.target.value)}
              >
                <option value="ALL">All MCPs</option>
                <option value="postgres">PostgreSQL</option>
                <option value="oracle">Oracle</option>
                <option value="mysql">MySQL</option>
                <option value="sqlserver">SQL Server</option>
                <option value="mongodb">MongoDB</option>
                <option value="redis">Redis</option>
                <option value="database">Generic database</option>
                <option value="web">Web</option>
              </select>
            </label>
            <span>{selected.length} selected</span>
          </div>
          <div className="capability-picker">
            {visible.map((c) => (
              <label key={c.capabilityId}>
                <input
                  type="checkbox"
                  checked={selected.includes(c.capabilityId)}
                  onChange={() => toggle(c.capabilityId)}
                />
                <span>
                  <b>{c.displayName || c.capabilityId}</b>
                  <small>{c.description || c.capabilityId}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        {selected.length > 0 && (
          <fieldset>
            <legend>Connection profile for each tool</legend>
            <p className="muted">
              Pin a named database, Redis, MCP, or API integration to this
              immutable agent version.
            </p>
            <div className="integration-binding-list">
              {selected.map((capability) => (
                <label key={capability}>
                  {capabilities.find((c) => c.capabilityId === capability)
                    ?.displayName || capability}
                  <select
                    value={capabilityProfiles[capability] || ""}
                    onChange={(e) =>
                      setCapabilityProfiles((current) => ({
                        ...current,
                        [capability]: e.target.value,
                      }))
                    }
                  >
                    <option value="">Automatic healthy provider</option>
                    {providerBindings
                      .filter((candidate) => candidate.capabilityId === capability)
                      .map((candidate) => (
                        <option
                          key={candidate.providerId}
                          value={candidate.providerId}
                        >
                          {candidate.providerId}
                        </option>
                      ))}
                  </select>
                </label>
              ))}
            </div>
          </fieldset>
        )}
        <fieldset>
          <legend>Run and deployment</legend>
          <div className="form-grid">
            <label>
              Execution placement
              <select
                value={placement}
                onChange={(e) => setPlacement(e.target.value)}
              >
                <option value="AUTO">Automatic</option>
                <option value="IN_PROCESS">In process · lightweight</option>
                <option value="DOCKER">Docker · isolated worker</option>
                <option value="KUBERNETES">Kubernetes pod · scalable</option>
              </select>
            </label>
            {trigger === "SCHEDULED" && (
              <label>
                Cron expression
                <input
                  required
                  value={cron}
                  onChange={(e) => setCron(e.target.value)}
                />
              </label>
            )}
            {trigger === "EVENT_DRIVEN" && (
              <label>
                Event source
                <input
                  required
                  value={eventSource}
                  onChange={(e) => setEventSource(e.target.value)}
                  placeholder="topic://orders.created"
                />
              </label>
            )}
          </div>
          <small>
            Docker runs use the isolated runtime worker. Kubernetes creates a
            portable Deployment and Service manifest that can be reviewed and
            applied from Deployments. Trigger configuration is versioned with
            the agent.
          </small>
        </fieldset>
        {pipeline.length > 0 && (
          <PipelineEditor
            pipeline={pipeline}
            skills={availableSkills}
            capabilities={capabilities}
            move={move}
          />
        )}
        {topology === "MULTI_AGENT" && (
          <fieldset>
            <legend>Member agents</legend>
            <div className="capability-picker">
              {agents
                .filter((a) => a.status === "ACTIVE" && a.id !== id)
                .map((a) => (
                  <label key={a.id}>
                    <input
                      type="checkbox"
                      checked={members.includes(a.id)}
                      onChange={() => toggleMember(a.id)}
                    />
                    <span>
                      <b>{a.displayName}</b>
                      <small>agent.{a.id}.invoke</small>
                    </span>
                  </label>
                ))}
            </div>
          </fieldset>
        )}
        <div className="publish-note">
          <b>
            {editing
              ? `Publishes version ${nextVersion()}`
              : mode === "compose"
                ? "Publishes a configured platform agent"
                : "Registers an isolated code agent"}
          </b>
          <span>
            Database credentials and endpoints belong to the selected MCP
            deployment, never inside the agent definition.
          </span>
        </div>
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy || modelMissing}>
            {busy
              ? "Saving…"
              : editing
                ? "Save new version"
                : mode === "compose"
                  ? "Create and publish"
                  : "Register code agent"}
          </button>
        </div>
      </form>
    </div>
  );
}
function PipelineEditor({
  pipeline,
  skills,
  capabilities,
  move,
}: {
  pipeline: string[];
  skills: Skill[];
  capabilities: Capability[];
  move: (index: number, delta: number) => void;
}) {
  return (
    <fieldset>
      <legend>Execution order</legend>
      <p className="muted">
        Skills and MCP tools share one pipeline. Each step receives prior
        outputs automatically.
      </p>
      <div className="pipeline-list">
        {pipeline.map((step, index) => (
          <div key={step}>
            <strong>{index + 1}</strong>
            <span>
              <b>
                {skills.find((s) => s.reference === step)?.displayName ||
                  capabilities.find((c) => c.capabilityId === step)
                    ?.displayName ||
                  step}
              </b>
              <small>
                {skills.some((s) => s.reference === step)
                  ? "Skill instruction"
                  : "MCP tool"}{" "}
                · receives prior step output
              </small>
            </span>
            <button
              type="button"
              aria-label={`Move ${step} up`}
              disabled={index === 0}
              onClick={() => move(index, -1)}
            >
              ↑
            </button>
            <button
              type="button"
              aria-label={`Move ${step} down`}
              disabled={index === pipeline.length - 1}
              onClick={() => move(index, 1)}
            >
              ↓
            </button>
          </div>
        ))}
      </div>
      <div className="publish-note">
        <b>LLM calls: on demand, maximum 2</b>
        <span>
          Deterministic tool chaining runs first. Tool results are reused; the
          model is called only for planning or final synthesis.
        </span>
      </div>
    </fieldset>
  );
}
function LegacyAgentModal({
  agents,
  capabilities,
  profiles,
  close,
  saved,
}: {
  agents: Agent[];
  capabilities: Capability[];
  profiles: ModelProfile[];
  close: () => void;
  saved: (message: string) => void;
}) {
  const [mode, setMode] = React.useState<"compose" | "import">("compose");
  const [id, setId] = React.useState("");
  const [name, setName] = React.useState("");
  const [team, setTeam] = React.useState("platform");
  const [description, setDescription] = React.useState("");
  const [model, setModel] = React.useState(
    profiles[0]?.profileId || "balanced-text",
  );
  const [skills, setSkills] = React.useState("");
  const [selected, setSelected] = React.useState<string[]>([]);
  const [members, setMembers] = React.useState<string[]>([]);
  const [interaction, setInteraction] = React.useState<
    "TASK" | "CHAT" | "TASK_AND_CHAT"
  >("TASK_AND_CHAT");
  const [topology, setTopology] = React.useState<
    "SINGLE_AGENT" | "MULTI_AGENT"
  >("SINGLE_AGENT");
  const [trigger, setTrigger] = React.useState<
    "ON_DEMAND" | "SCHEDULED" | "EVENT_DRIVEN"
  >("ON_DEMAND");
  const [repo, setRepo] = React.useState("https://github.com/ravikumar10/");
  const [busy, setBusy] = React.useState(false);
  const toggle = (value: string) =>
    setSelected((values) =>
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  const toggleMember = (value: string) =>
    setMembers((values) =>
      values.includes(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    try {
      await api("/api/v1/agents", {
        method: "POST",
        body: JSON.stringify({
          id,
          displayName: name,
          ownerTeam: team,
          description,
          interactionMode: interaction,
          topology,
          triggerMode: trigger,
          tags: [
            mode === "compose" ? "composed" : "code-backed",
            topology.toLowerCase(),
            interaction.toLowerCase(),
          ],
        }),
      });
      const skillRefs = skills
        .split("\n")
        .map((v) => v.trim())
        .filter(Boolean);
      const version = {
        agentId: id,
        version: "1.0.0",
        runtimeType: mode === "compose" ? "CONFIG" : "REMOTE_HTTP",
        hostingMode: mode === "compose" ? "PLATFORM" : "CLIENT",
        artifactRef: mode === "compose" ? `config://${id}/1.0.0` : repo,
        remoteEndpointRef: null,
        capabilitiesProvided: [`agent.${id}.invoke`],
        toolCapabilitiesRequired: selected,
        agentCapabilitiesRequired: members.map(
          (member) => `agent.${member}.invoke`,
        ),
        modelProfile: model || null,
        promptRef: skillRefs.length
          ? `skills://${encodeURIComponent(JSON.stringify(skillRefs))}`
          : null,
        inputSchemaRef: `catalog://schemas/${id}-input`,
        outputSchemaRef: `catalog://schemas/${id}-output`,
        executionPolicyRef: "policy://default-bounded",
        securityPolicyRef: "policy://tenant-default",
        checksum: `ui-${Date.now()}`,
      };
      await api(`/api/v1/agents/${id}/versions`, {
        method: "POST",
        body: JSON.stringify(version),
      });
      if (mode === "compose") {
        await api(`/api/v1/agents/${id}/versions/1.0.0/validate`, {
          method: "POST",
        });
        await api(`/api/v1/agents/${id}/versions/1.0.0/activate`, {
          method: "POST",
        });
        saved(
          "Agent 1.0.0 published with its interaction, topology, model, MCP, and skill bindings.",
        );
      } else
        saved(
          "Code-backed agent registered. Connect its isolated worker endpoint before activation.",
        );
    } finally {
      setBusy(false);
    }
  }
  return (
    <div
      className="backdrop"
      role="presentation"
      onMouseDown={(e) => {
        if (e.currentTarget === e.target) close();
      }}
    >
      <form className="modal builder" onSubmit={submit}>
        <div className="panel-title">
          <div>
            <p className="eyebrow">AGENT BUILDER</p>
            <h2>
              {mode === "compose" ? "Compose an agent" : "Import code agent"}
            </h2>
          </div>
          <button type="button" aria-label="Close" onClick={close}>
            ×
          </button>
        </div>
        <div className="mode-tabs">
          <button
            type="button"
            className={mode === "compose" ? "active" : ""}
            onClick={() => setMode("compose")}
          >
            Compose
          </button>
          <button
            type="button"
            className={mode === "import" ? "active" : ""}
            onClick={() => setMode("import")}
          >
            Import repository
          </button>
        </div>
        <div className="form-grid">
          <label>
            Agent ID
            <input
              required
              pattern="[a-z0-9][a-z0-9.-]{2,127}"
              value={id}
              onChange={(e) => setId(e.target.value)}
              placeholder="text-to-sql"
            />
          </label>
          <label>
            Display name
            <input
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          <label>
            Owner team
            <input
              required
              value={team}
              onChange={(e) => setTeam(e.target.value)}
            />
          </label>
        </div>
        <label>
          Description
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </label>
        <div className="form-grid contract-grid">
          <label>
            Interface
            <select
              value={interaction}
              onChange={(e) =>
                setInteraction(e.target.value as typeof interaction)
              }
            >
              <option value="TASK">Run once</option>
              <option value="CHAT">Chat</option>
              <option value="TASK_AND_CHAT">Run once + chat</option>
            </select>
          </label>
          <label>
            Topology
            <select
              value={topology}
              onChange={(e) => setTopology(e.target.value as typeof topology)}
            >
              <option value="SINGLE_AGENT">Single agent</option>
              <option value="MULTI_AGENT">Multi-agent system</option>
            </select>
          </label>
          <label>
            Trigger
            <select
              value={trigger}
              onChange={(e) => setTrigger(e.target.value as typeof trigger)}
            >
              <option value="ON_DEMAND">On demand</option>
              <option value="SCHEDULED">Scheduled</option>
              <option value="EVENT_DRIVEN">Event driven</option>
            </select>
          </label>
        </div>
        {mode === "import" && (
          <label>
            Git repository URL
            <input
              required
              type="url"
              value={repo}
              onChange={(e) => setRepo(e.target.value)}
            />
            <small>
              Custom code always runs out-of-process in an isolated
              worker/container.
            </small>
          </label>
        )}
        <div className="form-grid builder-config">
          <label>
            Logical model profile
            <select value={model} onChange={(e) => setModel(e.target.value)}>
              <option value="">No model</option>
              {profiles.map((p) => (
                <option key={p.profileId} value={p.profileId}>
                  {p.profileId}
                  {p.modelId ? ` · ${p.modelId}` : ""}
                </option>
              ))}
            </select>
          </label>
          <label>
            Skill references, one per line
            <textarea
              value={skills}
              onChange={(e) => setSkills(e.target.value)}
              placeholder={
                "github://owner/repo/skills/text-to-sql/SKILL.md\ngithub://owner/repo/skills/sql-safety/SKILL.md"
              }
            />
          </label>
        </div>
        <fieldset>
          <legend>Governed MCP capabilities</legend>
          <div className="capability-picker">
            {capabilities.map((c) => (
              <label key={c.capabilityId}>
                <input
                  type="checkbox"
                  checked={selected.includes(c.capabilityId)}
                  onChange={() => toggle(c.capabilityId)}
                />
                <span>
                  <b>{c.displayName || c.capabilityId}</b>
                  <small>{c.capabilityId}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>
        {topology === "MULTI_AGENT" && (
          <fieldset>
            <legend>Member agents</legend>
            <div className="capability-picker">
              {agents
                .filter((a) => a.status === "ACTIVE")
                .map((a) => (
                  <label key={a.id}>
                    <input
                      type="checkbox"
                      checked={members.includes(a.id)}
                      onChange={() => toggleMember(a.id)}
                    />
                    <span>
                      <b>{a.displayName}</b>
                      <small>agent.{a.id}.invoke</small>
                    </span>
                  </label>
                ))}
            </div>
          </fieldset>
        )}
        <div className="publish-note">
          <b>
            {mode === "compose"
              ? "Publishes a configured platform agent"
              : "Registers an isolated code agent"}
          </b>
          <span>
            Agents bind logical model profiles, capabilities, skills, and agent
            capabilities—never provider endpoints or raw MCP URLs.
          </span>
        </div>
        <div className="actions">
          <button type="button" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            {busy
              ? "Saving…"
              : mode === "compose"
                ? "Create and publish"
                : "Register code agent"}
          </button>
        </div>
      </form>
    </div>
  );
}
function Playground({
  agent,
  runs,
  streamState,
  close,
}: {
  agent: Agent;
  runs: Run[];
  streamState: "connecting" | "live" | "retrying";
  close: () => void;
}) {
  const defaultInput =
    agent.id === "website-reader"
      ? '{"url":"https://example.com","question":"Summarize this page and cite the source."}'
      : agent.id === "database-reader"
        ? '{"category":"electronics","question":"Which products are available?"}'
        : '{"question":"How can you help me?"}';
  const [input, setInput] = React.useState(defaultInput);
  const [runId, setRunId] = React.useState("");
  const [busy, setBusy] = React.useState(false);
  const [failure, setFailure] = React.useState("");
  const run = runs.find((item) => item.runId === runId);
  async function execute() {
    setBusy(true);
    setFailure("");
    try {
      const started = await api<Run>("/api/v1/runs", {
        method: "POST",
        body: JSON.stringify({
          agentId: agent.id,
          input: JSON.parse(input),
          subjectId: "studio-user",
          scopes: ["agents:invoke"],
          async: true,
        }),
      });
      setRunId(started.runId);
    } catch (e) {
      setFailure(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="backdrop">
      <section className="modal playground">
        <div className="panel-title">
          <div>
            <p className="eyebrow">AGENT PLAYGROUND</p>
            <h2>{agent.displayName}</h2>
            <code>{agent.id} · active version</code>
          </div>
          <button aria-label="Close" onClick={close}>
            ×
          </button>
        </div>
        <div className="playground-live">
          <span className={`stream-state ${streamState}`}>
            <i />
            {streamState === "live" ? "Live event stream" : streamState}
          </span>
          {run && (
            <span className={`status ${run.status.toLowerCase()}`}>
              {run.status}
            </span>
          )}
        </div>
        <label>
          Request JSON
          <textarea value={input} onChange={(e) => setInput(e.target.value)} />
        </label>
        <button
          className="primary run-button"
          disabled={busy}
          onClick={execute}
        >
          {busy ? "Starting…" : "Run agent"}
        </button>
        {failure && <div className="notice error">{failure}</div>}
        {run && (
          <div className="run-result">
            <div className="run-steps">
              <span className="done">Created</span>
              <span className={run.status === "CREATED" ? "" : "done"}>
                Running
              </span>
              <span
                className={
                  ["COMPLETED", "FAILED", "CANCELLED"].includes(run.status)
                    ? "done"
                    : ""
                }
              >
                Response
              </span>
            </div>
            <pre>
              {run.status === "COMPLETED"
                ? JSON.stringify(run.output, null, 2)
                : run.status === "FAILED"
                  ? JSON.stringify(run, null, 2)
                  : "Waiting for agent and tool events…"}
            </pre>
          </div>
        )}
      </section>
    </div>
  );
}
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
