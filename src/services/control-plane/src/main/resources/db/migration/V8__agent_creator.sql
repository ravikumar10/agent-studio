insert into capabilities(tenant_id,capability_id,spec) values
('local-development','platform.agents.create','{"tenantId":"local-development","capabilityId":"platform.agents.create","displayName":"Create governed agent","description":"Create and publish an agent definition from an approved Agent Creator conversation","kind":"TOOL","inputSchemaRef":"catalog://schemas/agent-create-input","outputSchemaRef":"catalog://schemas/agent-create-output","riskClass":"REVERSIBLE_WRITE","owner":"platform","tags":["platform","agent-management","write"]}')
on conflict do nothing;

insert into agents(tenant_id,id,display_name,description,owner_team,tags,interaction_mode,topology,trigger_mode,status,created_at,updated_at) values
('local-development','agent-creator','Agent Creator','Describe an agent in chat. This governed system agent creates a runnable agent using the default registered model and suggests MCP and skill bindings.','platform','["system","agent-builder"]','CHAT','SINGLE_AGENT','ON_DEMAND','ACTIVE',now(),now())
on conflict do nothing;

insert into agent_versions(tenant_id,agent_id,version,spec,lifecycle,checksum,created_at) values
('local-development','agent-creator','1.0.0','{"tenantId":"local-development","agentId":"agent-creator","version":"1.0.0","runtimeType":"CONFIG","hostingMode":"PLATFORM","artifactRef":"config://agent-creator/1.0.0","remoteEndpointRef":null,"capabilitiesProvided":["agent.agent-creator.invoke"],"toolCapabilitiesRequired":["platform.agents.create"],"agentCapabilitiesRequired":[],"modelProfile":"balanced-text","promptRef":"catalog://skills/task-planning","inputSchemaRef":"catalog://schemas/agent-creator-input","outputSchemaRef":"catalog://schemas/agent-creator-output","executionPolicyRef":"policy://agent-creator","securityPolicyRef":"policy://tenant-admin","checksum":"agent-creator-1.0.0","lifecycle":"ACTIVE","createdAt":"2026-09-25T00:00:00Z"}','ACTIVE','agent-creator-1.0.0',now())
on conflict do nothing;

insert into agent_release_state(tenant_id,agent_id,active_version,updated_at) values
('local-development','agent-creator','1.0.0',now())
on conflict (tenant_id,agent_id) do update set active_version=excluded.active_version,updated_at=excluded.updated_at;
