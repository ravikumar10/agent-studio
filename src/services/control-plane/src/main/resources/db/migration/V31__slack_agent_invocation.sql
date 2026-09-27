insert into capabilities(tenant_id,capability_id,spec)
select organization_id,'slack.agent.invoke',jsonb_build_object(
  'tenantId',organization_id,
  'capabilityId','slack.agent.invoke',
  'displayName','Slack · Invoke approved agent',
  'description','Validate a Slack app mention and create a governed asynchronous agent dispatch envelope',
  'kind','TOOL',
  'inputSchemaRef','catalog://schemas/slack-agent-invoke',
  'outputSchemaRef','catalog://schemas/agent-dispatch-envelope',
  'riskClass','CONTROLLED_WRITE',
  'owner','platform',
  'tags',jsonb_build_array('mcp','slack','trigger','agent','approval-required'))
from organization_accounts
on conflict (tenant_id,capability_id) do update set spec=excluded.spec;

insert into capability_provider_bindings(
  tenant_id,capability_id,provider_id,remote_operation_name,provider_version,
  routing_weight,enabled,compatibility
)
select tenant_id,'slack.agent.invoke',provider_id,'/tools/slack.agent.invoke','1.1.0',100,true,'{"method":"POST"}'::jsonb
from capability_providers
where integration_kind='SLACK' and enabled=true
on conflict(tenant_id,capability_id,provider_id) do update set
  remote_operation_name=excluded.remote_operation_name,
  provider_version=excluded.provider_version,
  enabled=true,
  compatibility=excluded.compatibility,
  updated_at=now();

update organization_integration_types
set definition=jsonb_set(
  jsonb_set(definition,'{suggestedCapabilities}',coalesce(definition->'suggestedCapabilities','[]'::jsonb) || '["slack.agent.invoke"]'::jsonb),
  '{fields}',coalesce(definition->'fields','[]'::jsonb) || '[{"key":"agentTrigger","label":"Agent invocation keyword","inputType":"TEXT","required":false,"secret":false,"placeholder":"call","defaultValue":"call","options":[]},{"key":"allowedAgentIds","label":"Allowed agent IDs (comma-separated; * for all)","inputType":"TEXT","required":false,"secret":false,"placeholder":"research-report-agent,database-reader","defaultValue":"","options":[]}]'::jsonb
), updated_at=now()
where integration_kind='SLACK'
  and not (definition->'suggestedCapabilities' ? 'slack.agent.invoke');
