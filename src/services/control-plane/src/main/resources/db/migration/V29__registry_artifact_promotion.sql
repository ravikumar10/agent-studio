alter table registry_artifacts drop constraint if exists registry_artifacts_state_check;
alter table registry_artifacts add constraint registry_artifacts_state_check
  check (state in ('DISCOVERED','PULLED','PROMOTED','FAILED'));
alter table registry_artifacts add column promoted_at timestamptz;
alter table registry_artifacts add column promotion_error text;

create index registry_artifacts_promotion_idx
  on registry_artifacts(tenant_id, artifact_type, state);
