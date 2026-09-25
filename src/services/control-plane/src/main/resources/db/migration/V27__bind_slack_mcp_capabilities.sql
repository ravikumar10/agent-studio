insert into capability_provider_bindings(
  tenant_id,capability_id,provider_id,remote_operation_name,provider_version,
  routing_weight,enabled,compatibility
)
select provider.tenant_id, capability.capability_id, provider.provider_id,
       capability.capability_id, '1.0.0', 100, true, '{}'::jsonb
from capability_providers provider
cross join (values ('slack.messages.read'),('slack.messages.send')) capability(capability_id)
where provider.integration_kind='SLACK'
  and provider.enabled=true
  and exists(
    select 1 from capabilities registered
    where registered.tenant_id=provider.tenant_id
      and registered.capability_id=capability.capability_id
  )
on conflict(tenant_id,capability_id,provider_id) do update set
  remote_operation_name=excluded.remote_operation_name,
  provider_version=excluded.provider_version,
  routing_weight=excluded.routing_weight,
  enabled=true,
  compatibility=excluded.compatibility,
  updated_at=now();
