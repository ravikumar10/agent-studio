create table model_connections (
  tenant_id varchar(128) not null,
  connection_id varchar(192) not null,
  display_name varchar(255) not null,
  provider varchar(32) not null check (provider in ('OPENAI','ANTHROPIC','OPENAI_COMPATIBLE')),
  base_url text not null,
  secret_ref text not null,
  organization_id varchar(255),
  project_id varchar(255),
  enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id,connection_id)
);

update model_profiles set spec=spec || '{"connectionId":null,"modelId":null,"generationParameters":{}}'::jsonb
where not (spec ? 'generationParameters');
