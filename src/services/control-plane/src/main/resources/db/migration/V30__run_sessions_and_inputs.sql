alter table runs add column if not exists session_id varchar(128);
alter table runs add column if not exists input jsonb not null default '{}'::jsonb;

update runs set session_id=run_id where session_id is null;
alter table runs alter column session_id set not null;

create index if not exists runs_session_idx
  on runs(tenant_id, session_id, created_at desc);
