insert into capabilities(tenant_id,capability_id,spec) values
('local-development','http.request','{"tenantId":"local-development","capabilityId":"http.request","displayName":"Governed HTTP request","description":"Invoke an allow-listed HTTP endpoint through a configured MCP profile","kind":"TOOL","inputSchemaRef":"catalog://schemas/http-request-input","outputSchemaRef":"catalog://schemas/http-response","riskClass":"LOW_RISK_WRITE","owner":"platform","tags":["mcp","http"]}'),
('local-development','browser.navigate','{"tenantId":"local-development","capabilityId":"browser.navigate","displayName":"Navigate with headless browser","description":"Open an allow-listed webpage in an isolated browser context","kind":"TOOL","inputSchemaRef":"catalog://schemas/browser-navigate-input","outputSchemaRef":"catalog://schemas/browser-page","riskClass":"READ_ONLY","owner":"platform","tags":["mcp","browser"]}'),
('local-development','browser.extract','{"tenantId":"local-development","capabilityId":"browser.extract","displayName":"Extract rendered webpage","description":"Extract text and metadata after JavaScript rendering","kind":"TOOL","inputSchemaRef":"catalog://schemas/browser-extract-input","outputSchemaRef":"catalog://schemas/browser-extract-output","riskClass":"READ_ONLY","owner":"platform","tags":["mcp","browser"]}'),
('local-development','browser.act','{"tenantId":"local-development","capabilityId":"browser.act","displayName":"Perform browser actions","description":"Perform a bounded sequence of configured browser interactions","kind":"TOOL","inputSchemaRef":"catalog://schemas/browser-action-input","outputSchemaRef":"catalog://schemas/browser-action-output","riskClass":"REVERSIBLE_WRITE","owner":"platform","tags":["mcp","browser","action"]}')
on conflict do nothing;

insert into capability_providers(tenant_id,provider_id,owner_user_id,display_name,provider_type,transport,endpoint_ref,configuration,health_status,integration_kind,schema_version)
values
('local-development','local-http-mcp','studio-user','Local governed HTTP MCP','MCP','HTTP','http://web-reader-tool:8080','{"healthPath":"/actuator/health","allowedHosts":"example.com,www.example.com","allowedMethods":"GET,HEAD","timeoutSeconds":10}','UNKNOWN','HTTP_API',1),
('local-development','local-browser-mcp','studio-user','Local headless browser MCP','MCP','HTTP','http://browser-mcp:8080','{"healthPath":"/health","allowedHosts":"example.com,www.example.com","headless":true,"navigationTimeoutSeconds":20,"maxActions":8}','UNKNOWN','HEADLESS_BROWSER',1)
on conflict do nothing;

insert into capability_provider_bindings(tenant_id,capability_id,provider_id,remote_operation_name,compatibility) values
('local-development','http.request','local-http-mcp','/tools/http.request','{"method":"POST"}'),
('local-development','browser.navigate','local-browser-mcp','/tools/browser.navigate','{"method":"POST"}'),
('local-development','browser.extract','local-browser-mcp','/tools/browser.extract','{"method":"POST"}'),
('local-development','browser.act','local-browser-mcp','/tools/browser.act','{"method":"POST"}')
on conflict do nothing;
