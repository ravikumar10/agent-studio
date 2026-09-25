insert into capabilities(tenant_id,capability_id,spec) values
('local-development','knowledge.search','{"tenantId":"local-development","capabilityId":"knowledge.search","displayName":"Search conversation knowledge","description":"Retrieve relevant agent-scoped content from Redis vector memory","kind":"TOOL","inputSchemaRef":"catalog://schemas/knowledge-search","outputSchemaRef":"catalog://schemas/knowledge-results","riskClass":"READ_ONLY","owner":"platform","tags":["mcp","knowledge","vector"]}'),
('local-development','knowledge.store','{"tenantId":"local-development","capabilityId":"knowledge.store","displayName":"Preserve conversation knowledge","description":"Store grounded content in tenant and agent isolated Redis vector memory","kind":"TOOL","inputSchemaRef":"catalog://schemas/knowledge-store","outputSchemaRef":"catalog://schemas/knowledge-record","riskClass":"REVERSIBLE_WRITE","owner":"platform","tags":["mcp","knowledge","vector"]}'),
('local-development','chart.generate','{"tenantId":"local-development","capabilityId":"chart.generate","displayName":"Generate chart","description":"Create a bounded Vega-Lite chart from MCP-provided tabular data","kind":"TOOL","inputSchemaRef":"catalog://schemas/chart-request","outputSchemaRef":"catalog://schemas/vega-lite","riskClass":"READ_ONLY","owner":"platform","tags":["mcp","chart","visualization"]}')
on conflict do nothing;

insert into capability_providers(tenant_id,provider_id,owner_user_id,display_name,provider_type,transport,configuration,health_status,integration_kind,schema_version)
values('local-development','local-content-mcp','studio-user','Studio knowledge and chart MCP','IN_PROCESS','IN_PROCESS','{}','HEALTHY','CUSTOM_ADAPTER',1)
on conflict do nothing;

insert into capability_provider_bindings(tenant_id,capability_id,provider_id,remote_operation_name,compatibility) values
('local-development','knowledge.search','local-content-mcp','local:knowledge.search','{}'),
('local-development','knowledge.store','local-content-mcp','local:knowledge.store','{}'),
('local-development','chart.generate','local-content-mcp','local:chart.generate','{}')
on conflict do nothing;
