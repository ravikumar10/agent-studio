create table agent_sessions (
  tenant_id varchar(128) not null,
  session_id varchar(128) not null,
  agent_id varchar(192) not null,
  initial_agent_version varchar(64) not null,
  subject_id varchar(192) not null,
  status varchar(32) not null default 'ACTIVE',
  memory_policy varchar(32) not null default 'DURABLE',
  summary text,
  created_at timestamptz not null default now(),
  last_activity_at timestamptz not null default now(),
  expires_at timestamptz,
  primary key (tenant_id, session_id),
  constraint agent_sessions_status_check check (status in ('ACTIVE','WAITING','COMPLETED','CLOSED','EXPIRED')),
  constraint agent_sessions_memory_policy_check check (memory_policy in ('NONE','SESSION','DURABLE','LONG_RUNNING'))
);

create table session_turns (
  tenant_id varchar(128) not null,
  session_id varchar(128) not null,
  turn_id uuid not null,
  run_id varchar(64),
  role varchar(32) not null,
  content jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  primary key (tenant_id, session_id, turn_id),
  constraint session_turns_role_check check (role in ('USER','ASSISTANT','SYSTEM','TOOL','ERROR')),
  constraint session_turns_session_fk foreign key (tenant_id, session_id)
    references agent_sessions(tenant_id, session_id) on delete cascade
);

create table session_evidence (
  tenant_id varchar(128) not null,
  session_id varchar(128) not null,
  evidence_id uuid not null,
  run_id varchar(64),
  capability_id varchar(192),
  content jsonb not null default '{}'::jsonb,
  content_hash varchar(64) not null,
  classification varchar(64) not null default 'INTERNAL',
  created_at timestamptz not null default now(),
  expires_at timestamptz,
  primary key (tenant_id, session_id, evidence_id),
  constraint session_evidence_session_fk foreign key (tenant_id, session_id)
    references agent_sessions(tenant_id, session_id) on delete cascade
);

create unique index session_evidence_dedup_idx
  on session_evidence(tenant_id, session_id, content_hash);
create index session_turns_recent_idx
  on session_turns(tenant_id, session_id, created_at desc);
create index session_evidence_recent_idx
  on session_evidence(tenant_id, session_id, created_at desc);
create index agent_sessions_expiry_idx
  on agent_sessions(expires_at) where expires_at is not null;

insert into agent_sessions(tenant_id,session_id,agent_id,initial_agent_version,subject_id,status,memory_policy,created_at,last_activity_at)
select tenant_id,session_id,min(agent_id),min(agent_version),'legacy','COMPLETED','DURABLE',min(created_at),max(coalesce(completed_at,started_at,created_at))
from runs
where session_id is not null
group by tenant_id,session_id
on conflict do nothing;
