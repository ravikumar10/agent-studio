create table organization_accounts (
  organization_id varchar(128) primary key,
  display_name varchar(255) not null,
  status varchar(32) not null default 'ACTIVE',
  settings jsonb not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table organization_memberships (
  organization_id varchar(128) not null references organization_accounts(organization_id) on delete cascade,
  user_id varchar(128) not null,
  role varchar(64) not null default 'MEMBER',
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  primary key (organization_id,user_id)
);

insert into organization_accounts(organization_id,display_name,settings)
select distinct tenant_id,'Local Development',
       '{"observability":{"defaultRange":"24h","refreshSeconds":10},"studio":{"enabledModules":["agents","deployments","registries","memory","runs","observability","capabilities","models","profile"]}}'::jsonb
from user_profiles on conflict do nothing;

insert into organization_memberships(organization_id,user_id,role)
select tenant_id,user_id,'OWNER' from user_profiles on conflict do nothing;

comment on table organization_accounts is 'Organization-scoped Studio configuration loaded during authenticated runtime bootstrap.';
comment on table organization_memberships is 'Maps authenticated users to organization accounts without coupling domain contracts to an identity provider.';
