create table registries (
  tenant_id varchar(128) not null,
  registry_id varchar(192) not null,
  registry_type varchar(32) not null check (registry_type in ('AGENT','MCP','SKILL')),
  display_name varchar(255) not null,
  source_type varchar(32) not null,
  source_uri text not null,
  owner varchar(255) not null,
  discovery_pattern varchar(512),
  status varchar(32) not null,
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, registry_id)
);

create table cold_memory (
  tenant_id varchar(128) not null,
  namespace varchar(192) not null,
  memory_key varchar(512) not null,
  content jsonb not null,
  classification varchar(64) not null default 'INTERNAL',
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (tenant_id, namespace, memory_key)
);

insert into registries(tenant_id,registry_id,registry_type,display_name,source_type,source_uri,owner,discovery_pattern,status,metadata)
values
 ('local-development','github-ravikumar10-agents','AGENT','ravikumar10 Agent Registry','GITHUB','https://github.com/ravikumar10','ravikumar10','**/agent.{yaml,yml,json}, **/agent-card.json','ACTIVE','{"repositoryCount":46,"defaultTrust":"UNVERIFIED","syncMode":"MANUAL"}'),
 ('local-development','github-ravikumar10-mcp','MCP','ravikumar10 MCP Registry','GITHUB','https://github.com/ravikumar10','ravikumar10','**/.mcp.json, **/mcp-server.{yaml,yml,json}','ACTIVE','{"repositoryCount":46,"transportPreference":"STREAMABLE_HTTP","publishPolicy":"EXPLICIT_APPROVAL"}'),
 ('local-development','github-ravikumar10-skills','SKILL','ravikumar10 Skill Registry','GITHUB','https://github.com/ravikumar10','ravikumar10','**/SKILL.md, **/.agents/skills/**','ACTIVE','{"repositoryCount":46,"loadMode":"PROGRESSIVE_DISCLOSURE","publishPolicy":"EXPLICIT_APPROVAL"}')
on conflict do nothing;
