create table agent_public_exposures (
  tenant_id varchar(128) not null,
  agent_id varchar(128) not null,
  agent_version varchar(64) not null,
  public_id varchar(160) not null unique,
  api_enabled boolean not null default false,
  widget_enabled boolean not null default false,
  access_mode varchar(24) not null default 'PUBLIC',
  api_key_hash varchar(128),
  allowed_origins jsonb not null default '["*"]',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, agent_id, agent_version),
  foreign key (tenant_id, agent_id, agent_version)
    references agent_versions(tenant_id, agent_id, version) on delete cascade,
  check (access_mode in ('PUBLIC', 'API_KEY')),
  check (api_enabled or widget_enabled)
);

create index idx_agent_public_exposures_public_id
  on agent_public_exposures(public_id) where api_enabled or widget_enabled;
