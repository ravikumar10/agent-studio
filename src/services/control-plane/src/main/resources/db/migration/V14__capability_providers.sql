create table capability_providers (
  tenant_id varchar(128) not null, provider_id varchar(192) not null, owner_user_id varchar(128) not null,
  display_name varchar(255) not null, provider_type varchar(32) not null, transport varchar(32) not null,
  endpoint_ref text, auth_type varchar(32) not null default 'NONE', secret_ref text,
  configuration jsonb not null default '{}', environment varchar(64) not null default 'local',
  health_status varchar(32) not null default 'UNKNOWN', last_verified_at timestamptz, last_error text,
  enabled boolean not null default true, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  primary key (tenant_id, provider_id),
  check (provider_type in ('MCP','REST','GRPC','EVENT','DB','AGENT','IN_PROCESS')),
  check (auth_type in ('NONE','API_KEY','BEARER','BASIC','OAUTH2','WORKLOAD_IDENTITY'))
);
create table capability_provider_bindings (
  tenant_id varchar(128) not null, capability_id varchar(192) not null, provider_id varchar(192) not null,
  remote_operation_name varchar(255) not null, provider_version varchar(64) not null default '1.0.0',
  routing_weight integer not null default 100, enabled boolean not null default true,
  compatibility jsonb not null default '{}', created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  primary key (tenant_id, capability_id, provider_id),
  foreign key (tenant_id, capability_id) references capabilities(tenant_id, capability_id) on delete cascade,
  foreign key (tenant_id, provider_id) references capability_providers(tenant_id, provider_id) on delete cascade
);
create index capability_provider_resolution_idx on capability_provider_bindings(tenant_id, capability_id, enabled, routing_weight desc);

insert into capability_providers(tenant_id,provider_id,owner_user_id,display_name,provider_type,transport,endpoint_ref,configuration,health_status)
values
('local-development','local-database-mcp','studio-user','Local database MCP','MCP','HTTP','http://database-reader-tool:8080','{"healthPath":"/actuator/health"}','UNKNOWN'),
('local-development','local-web-mcp','studio-user','Local web MCP','MCP','HTTP','http://web-reader-tool:8080','{"healthPath":"/actuator/health"}','UNKNOWN'),
('local-development','local-redis-adapter','studio-user','Studio Redis adapter','IN_PROCESS','IN_PROCESS',null,'{}','HEALTHY');

insert into capability_provider_bindings(tenant_id,capability_id,provider_id,remote_operation_name)
select tenant_id,capability_id,
 case when capability_id like 'redis.%' then 'local-redis-adapter' when capability_id like 'web.%' then 'local-web-mcp' else 'local-database-mcp' end,
 case when capability_id like '%.describe-schema' then '/tools/database.describe-schema'
      when capability_id like '%.query-readonly' then '/tools/database.query-readonly'
      when capability_id='web.fetch' then '/tools/web.fetch'
      when capability_id='web.extract' then 'local:web.extract'
      else capability_id end
from capabilities where tenant_id='local-development' and (capability_id like 'redis.%' or capability_id like 'web.%' or capability_id like '%.describe-schema' or capability_id like '%.query-readonly')
on conflict do nothing;
