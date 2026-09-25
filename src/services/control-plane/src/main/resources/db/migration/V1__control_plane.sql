create table agents (
  tenant_id varchar(128) not null, id varchar(128) not null, display_name varchar(255) not null,
  description text not null default '', owner_team varchar(255) not null, tags jsonb not null default '[]',
  status varchar(32) not null, created_at timestamptz not null, updated_at timestamptz not null,
  primary key (tenant_id, id)
);
create table agent_versions (
  tenant_id varchar(128) not null, agent_id varchar(128) not null, version varchar(64) not null,
  spec jsonb not null, lifecycle varchar(32) not null, checksum varchar(255), created_at timestamptz not null,
  primary key (tenant_id, agent_id, version),
  foreign key (tenant_id, agent_id) references agents(tenant_id, id)
);
create table agent_release_state (
  tenant_id varchar(128) not null, agent_id varchar(128) not null, active_version varchar(64) not null,
  updated_at timestamptz not null, primary key (tenant_id, agent_id),
  foreign key (tenant_id, agent_id, active_version) references agent_versions(tenant_id, agent_id, version)
);
create table capabilities (
  tenant_id varchar(128) not null, capability_id varchar(192) not null, spec jsonb not null,
  created_at timestamptz not null default now(), primary key (tenant_id, capability_id)
);
create table model_profiles (
  tenant_id varchar(128) not null, profile_id varchar(192) not null, spec jsonb not null,
  created_at timestamptz not null default now(), primary key (tenant_id, profile_id)
);
create index agent_versions_lifecycle_idx on agent_versions(tenant_id, lifecycle);
create table runs (
  tenant_id varchar(128) not null, run_id varchar(64) not null, agent_id varchar(128) not null,
  agent_version varchar(64) not null, status varchar(32) not null, created_at timestamptz not null,
  started_at timestamptz, completed_at timestamptz, output jsonb, error text,
  primary key (tenant_id, run_id),
  foreign key (tenant_id, agent_id, agent_version) references agent_versions(tenant_id, agent_id, version)
);
create table run_events (
  sequence bigint generated always as identity, tenant_id varchar(128) not null, run_id varchar(64) not null,
  event_type varchar(128) not null, attributes jsonb not null default '{}', occurred_at timestamptz not null,
  primary key (tenant_id, run_id, sequence), foreign key (tenant_id, run_id) references runs(tenant_id, run_id)
);
