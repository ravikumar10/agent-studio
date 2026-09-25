update capability_providers
set endpoint_ref='http://slack-mcp:8080',
    transport='HTTP',
    health_status='UNKNOWN',
    last_error=null,
    updated_at=now()
where integration_kind='SLACK'
  and (endpoint_ref is null or endpoint_ref='https://tool-gateway.example.com');

update capability_provider_bindings
set remote_operation_name=case capability_id
      when 'slack.messages.read' then '/tools/slack.messages.read'
      when 'slack.messages.send' then '/tools/slack.messages.send'
      else remote_operation_name end,
    compatibility='{"method":"POST"}'::jsonb,
    enabled=true,
    updated_at=now()
where capability_id in ('slack.messages.read','slack.messages.send')
  and provider_id in (
    select provider_id from capability_providers
    where capability_provider_bindings.tenant_id=capability_providers.tenant_id
      and integration_kind='SLACK'
  );
