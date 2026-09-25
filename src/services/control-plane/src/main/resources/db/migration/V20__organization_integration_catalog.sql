create table organization_integration_types (
    organization_id varchar(128) not null references organization_accounts(organization_id) on delete cascade,
    integration_kind varchar(96) not null,
    definition jsonb not null,
    enabled boolean not null default true,
    sort_order integer not null default 100,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (organization_id, integration_kind)
);

create index organization_integration_types_enabled_idx
    on organization_integration_types(organization_id, enabled, sort_order);
