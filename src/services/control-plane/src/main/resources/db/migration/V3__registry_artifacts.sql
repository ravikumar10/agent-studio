create table registry_artifacts (
  tenant_id varchar(128) not null,
  registry_id varchar(128) not null,
  artifact_id varchar(128) not null,
  artifact_type varchar(16) not null check (artifact_type in ('AGENT','MCP','SKILL')),
  name varchar(255) not null,
  version varchar(64) not null,
  path varchar(1024) not null,
  description text not null default '',
  content text,
  state varchar(16) not null default 'DISCOVERED' check (state in ('DISCOVERED','PULLED')),
  synced_at timestamptz not null default now(),
  pulled_at timestamptz,
  primary key (tenant_id, registry_id, artifact_id, version),
  foreign key (tenant_id, registry_id) references registries(tenant_id, registry_id) on delete cascade
);
create index registry_artifacts_lookup on registry_artifacts(tenant_id, registry_id, artifact_type);

-- Replace the broad account-level examples with one conventional catalog repository.
update registries set source_uri='https://github.com/ravikumar10/agent-studio-sample-registry',
  metadata=metadata || '{"branch":"main","manifest":"catalog.json","syncMode":"MANUAL"}'::jsonb
where tenant_id='local-development' and registry_id like 'github-ravikumar10-%';
