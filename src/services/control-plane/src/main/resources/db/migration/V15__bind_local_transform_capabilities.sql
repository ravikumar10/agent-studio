insert into capability_providers(tenant_id,provider_id,owner_user_id,display_name,provider_type,transport,configuration,health_status)
values('local-development','local-transform-adapter','studio-user','Studio transform adapter','IN_PROCESS','IN_PROCESS','{}','HEALTHY')
on conflict do nothing;
update capability_provider_bindings set provider_id='local-transform-adapter',updated_at=now()
where tenant_id='local-development' and capability_id='web.extract' and provider_id='local-web-mcp';
