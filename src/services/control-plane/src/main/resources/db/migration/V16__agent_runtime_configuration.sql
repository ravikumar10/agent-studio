create table agent_runtime_configurations (
  tenant_id varchar(128) not null, agent_id varchar(128) not null, agent_version varchar(64) not null,
  owner_user_id varchar(128) not null, execution_placement varchar(32) not null default 'IN_PROCESS',
  trigger_type varchar(32) not null default 'ON_DEMAND', trigger_configuration jsonb not null default '{}',
  resource_configuration jsonb not null default '{}', created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
  primary key(tenant_id,agent_id,agent_version),
  foreign key(tenant_id,agent_id,agent_version) references agent_versions(tenant_id,agent_id,version) on delete cascade,
  check(execution_placement in ('AUTO','IN_PROCESS','DOCKER','KUBERNETES')),
  check(trigger_type in ('ON_DEMAND','SCHEDULED','EVENT_DRIVEN','WEBHOOK'))
);
create table agent_capability_profile_bindings (
  tenant_id varchar(128) not null, agent_id varchar(128) not null, agent_version varchar(64) not null,
  capability_id varchar(192) not null, provider_id varchar(192) not null, created_at timestamptz not null default now(),
  primary key(tenant_id,agent_id,agent_version,capability_id),
  foreign key(tenant_id,agent_id,agent_version) references agent_versions(tenant_id,agent_id,version) on delete cascade,
  foreign key(tenant_id,capability_id,provider_id) references capability_provider_bindings(tenant_id,capability_id,provider_id)
);

insert into agent_runtime_configurations(tenant_id,agent_id,agent_version,owner_user_id,execution_placement,trigger_type)
select v.tenant_id,v.agent_id,v.version,coalesce(v.created_by_user_id,a.owner_user_id,'studio-user'),
 case when v.spec->>'runtimeType'='CONFIG' then 'IN_PROCESS' else 'DOCKER' end,
 coalesce(a.trigger_mode,'ON_DEMAND')
from agent_versions v join agents a on a.tenant_id=v.tenant_id and a.id=v.agent_id
on conflict do nothing;
