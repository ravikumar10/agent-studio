alter table registries add column owner_user_id varchar(128) not null default 'studio-user';
alter table registries add column last_synced_at timestamptz;
alter table registries add column sync_status varchar(32) not null default 'NOT_SYNCED';
alter table registries add column sync_error text;

create index registries_owner_user_idx on registries(tenant_id, owner_user_id);

-- Existing registry rows predate user profiles and belong to the local Studio user.
update registries set owner_user_id='studio-user' where owner_user_id is null;

comment on column registries.sync_status is 'NOT_SYNCED, SYNCING, SYNCED, or FAILED. Updated by every integration sync attempt.';
