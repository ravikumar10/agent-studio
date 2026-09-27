create table slack_inbound_events (
  event_id varchar(160) primary key,
  tenant_id varchar(128) not null,
  provider_id varchar(128) not null,
  channel_id varchar(128),
  thread_ts varchar(64),
  run_id varchar(64),
  status varchar(32) not null default 'ACCEPTED',
  error varchar(1000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index slack_inbound_events_tenant_created_idx
  on slack_inbound_events(tenant_id,created_at desc);
