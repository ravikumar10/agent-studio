-- web.fetch predates named integration profiles. Bind it to the governed HTTP
-- MCP so agents created from natural language never depend on a deleted legacy
-- provider profile.
insert into capability_provider_bindings(
  tenant_id,capability_id,provider_id,remote_operation_name,provider_version,
  routing_weight,enabled,compatibility
) values (
  'local-development','web.fetch','local-http-mcp','/tools/web.fetch','1.0.0',
  100,true,'{"method":"POST"}'::jsonb
)
on conflict(tenant_id,capability_id,provider_id) do update set
  remote_operation_name=excluded.remote_operation_name,
  provider_version=excluded.provider_version,
  routing_weight=excluded.routing_weight,
  enabled=true,
  compatibility=excluded.compatibility,
  updated_at=now();

insert into capability_provider_bindings(
  tenant_id,capability_id,provider_id,remote_operation_name,provider_version,
  routing_weight,enabled,compatibility
) values (
  'local-development','web.extract','local-transform-adapter','local:web.extract',
  '1.0.0',100,true,'{}'::jsonb
)
on conflict(tenant_id,capability_id,provider_id) do update set
  enabled=true,updated_at=now();
