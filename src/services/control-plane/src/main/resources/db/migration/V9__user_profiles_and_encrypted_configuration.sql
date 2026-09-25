create table user_profiles (
  tenant_id varchar(128) not null,
  user_id varchar(128) not null,
  display_name varchar(255) not null,
  email varchar(320),
  preferences jsonb not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, user_id)
);

create table user_configurations (
  tenant_id varchar(128) not null,
  user_id varchar(128) not null,
  namespace varchar(128) not null,
  config_key varchar(192) not null,
  config_value jsonb not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, user_id, namespace, config_key),
  foreign key (tenant_id, user_id) references user_profiles(tenant_id, user_id) on delete cascade
);

create table user_secrets (
  tenant_id varchar(128) not null,
  user_id varchar(128) not null,
  secret_id varchar(192) not null,
  secret_type varchar(64) not null,
  ciphertext text not null,
  initialization_vector varchar(64) not null,
  key_version varchar(32) not null default 'v1',
  last_four varchar(4),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, user_id, secret_id),
  foreign key (tenant_id, user_id) references user_profiles(tenant_id, user_id) on delete cascade
);

insert into user_profiles(tenant_id,user_id,display_name,preferences)
select distinct tenant_id,'studio-user','Studio User','{}'::jsonb from agents
on conflict do nothing;

alter table agents add column owner_user_id varchar(128) not null default 'studio-user';
alter table agent_versions add column created_by_user_id varchar(128) not null default 'studio-user';
alter table model_connections add column owner_user_id varchar(128) not null default 'studio-user';
alter table model_profiles add column owner_user_id varchar(128) not null default 'studio-user';

create index agents_owner_user_idx on agents(tenant_id, owner_user_id);
create index model_connections_owner_user_idx on model_connections(tenant_id, owner_user_id);
create index user_configurations_namespace_idx on user_configurations(tenant_id, user_id, namespace);

comment on table user_secrets is 'AES-GCM encrypted user secrets. Encryption keys are supplied at runtime and never stored in this database.';
comment on table user_configurations is 'Non-secret per-user configuration. Sensitive values belong in user_secrets.';
