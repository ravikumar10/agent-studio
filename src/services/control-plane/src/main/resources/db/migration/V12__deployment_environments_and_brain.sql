create table deployment_environments (
  tenant_id varchar(128) not null,
  environment_id varchar(128) not null,
  owner_user_id varchar(128) not null,
  display_name varchar(255) not null,
  target_type varchar(32) not null check (target_type in ('STUDIO','KUBERNETES')),
  cloud_provider varchar(32) not null check (cloud_provider in ('LOCAL','AZURE','AWS','GCP','OPENSHIFT','ON_PREM')),
  namespace varchar(128) not null,
  adapter_config jsonb not null default '{}',
  credential_ref varchar(512),
  status varchar(32) not null default 'READY',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, environment_id)
);

create table deployment_plans (
  tenant_id varchar(128) not null,
  deployment_id varchar(64) not null,
  owner_user_id varchar(128) not null,
  environment_id varchar(128) not null,
  agent_id varchar(128) not null,
  agent_version varchar(64) not null,
  deployment_mode varchar(32) not null check (deployment_mode in ('STUDIO','KUBERNETES')),
  desired_state varchar(32) not null,
  observed_state varchar(32) not null,
  image_ref varchar(512),
  replicas integer not null default 1 check (replicas between 0 and 100),
  manifest_yaml text not null,
  configuration jsonb not null default '{}',
  last_error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, deployment_id),
  foreign key (tenant_id, environment_id) references deployment_environments(tenant_id, environment_id),
  foreign key (tenant_id, agent_id, agent_version) references agent_versions(tenant_id, agent_id, version)
);

create table agent_schedules (
  tenant_id varchar(128) not null,
  schedule_id varchar(128) not null,
  owner_user_id varchar(128) not null,
  deployment_id varchar(64) not null,
  cron_expression varchar(128) not null,
  time_zone varchar(128) not null default 'UTC',
  enabled boolean not null default true,
  input jsonb not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, schedule_id),
  foreign key (tenant_id, deployment_id) references deployment_plans(tenant_id, deployment_id) on delete cascade
);

create table brain_profiles (
  tenant_id varchar(128) not null,
  profile_id varchar(128) not null,
  owner_user_id varchar(128) not null,
  display_name varchar(255) not null,
  model_profile varchar(192),
  policy jsonb not null,
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, profile_id)
);

create index deployment_plans_agent_idx on deployment_plans(tenant_id, agent_id, created_at desc);
create index agent_schedules_enabled_idx on agent_schedules(tenant_id, enabled);

insert into deployment_environments(
  tenant_id,environment_id,owner_user_id,display_name,target_type,cloud_provider,namespace,adapter_config,status
) values (
  'local-development','studio-local','studio-user','Agent Studio local runtime','STUDIO','LOCAL','agent-studio',
  '{"runtime":"runtime-service","strategy":"PINNED_VERSION"}','READY'
) on conflict do nothing;

insert into brain_profiles(tenant_id,profile_id,owner_user_id,display_name,policy)
values ('local-development','default-brain','studio-user','Default orchestration brain',
  '{"planner":"DETERMINISTIC_FIRST","maxModelCalls":2,"maxToolCalls":12,"reuseToolResults":true,"requireApprovalForSideEffects":true,"retryPolicy":{"maxAttempts":3,"backoff":"EXPONENTIAL"},"deploymentPolicy":{"strategy":"ROLLING","healthGate":true,"autoRollback":true}}')
on conflict do nothing;

insert into capabilities(tenant_id,capability_id,spec) values
('local-development','kubernetes.plan-workload','{"tenantId":"local-development","capabilityId":"kubernetes.plan-workload","displayName":"Kubernetes · Build workload plan","description":"Generate a portable Kubernetes deployment plan without changing a cluster","kind":"TOOL","inputSchemaRef":"catalog://schemas/kubernetes-plan-input","outputSchemaRef":"catalog://schemas/kubernetes-manifest-output","riskClass":"READ_ONLY","owner":"platform","tags":["mcp","kubernetes","deployment"]}'),
('local-development','kubernetes.apply-workload','{"tenantId":"local-development","capabilityId":"kubernetes.apply-workload","displayName":"Kubernetes · Apply workload","description":"Apply an approved stored manifest through a configured Kubernetes adapter","kind":"TOOL","inputSchemaRef":"catalog://schemas/kubernetes-apply-input","outputSchemaRef":"catalog://schemas/kubernetes-status-output","riskClass":"SIDE_EFFECT","owner":"platform","tags":["mcp","kubernetes","deployment","approval-required"]}'),
('local-development','kubernetes.get-rollout','{"tenantId":"local-development","capabilityId":"kubernetes.get-rollout","displayName":"Kubernetes · Get rollout status","description":"Read deployment and pod health for a stored workload","kind":"TOOL","inputSchemaRef":"catalog://schemas/kubernetes-status-input","outputSchemaRef":"catalog://schemas/kubernetes-status-output","riskClass":"READ_ONLY","owner":"platform","tags":["mcp","kubernetes","observability"]}'),
('local-development','kubernetes.rollback-workload','{"tenantId":"local-development","capabilityId":"kubernetes.rollback-workload","displayName":"Kubernetes · Roll back workload","description":"Roll an approved deployment back to its previously healthy revision","kind":"TOOL","inputSchemaRef":"catalog://schemas/kubernetes-rollback-input","outputSchemaRef":"catalog://schemas/kubernetes-status-output","riskClass":"SIDE_EFFECT","owner":"platform","tags":["mcp","kubernetes","deployment","approval-required"]}')
on conflict do nothing;
