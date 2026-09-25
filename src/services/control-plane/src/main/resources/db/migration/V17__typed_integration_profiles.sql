alter table capability_providers
  add column integration_kind varchar(64),
  add column schema_version integer not null default 1;

update capability_providers set integration_kind = case
  when provider_id = 'local-database-mcp' then 'POSTGRESQL'
  when provider_id = 'local-web-mcp' then 'MCP_SERVER'
  when provider_id = 'local-redis-adapter' then 'REDIS'
  when provider_type = 'MCP' then 'MCP_SERVER'
  when provider_type = 'REST' then 'HTTP_API'
  when provider_type = 'EVENT' then 'KAFKA'
  else 'CUSTOM_ADAPTER'
end where integration_kind is null;

alter table capability_providers alter column integration_kind set not null;

create table capability_provider_secrets (
  tenant_id varchar(128) not null,
  provider_id varchar(192) not null,
  credential_key varchar(96) not null,
  secret_ref text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, provider_id, credential_key),
  foreign key (tenant_id, provider_id)
    references capability_providers(tenant_id, provider_id) on delete cascade
);

create index capability_provider_kind_idx
  on capability_providers(tenant_id, integration_kind, enabled);
