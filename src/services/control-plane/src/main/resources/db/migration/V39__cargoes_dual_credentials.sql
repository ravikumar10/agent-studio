update capability_providers
set configuration = jsonb_set(configuration, '{authMode}', '"API_KEY_AND_ORG_TOKEN"'::jsonb, true),
    updated_at = now()
where integration_kind like 'REGISTRY_%CARGOES_FL%';

update organization_integration_types
set definition = jsonb_set(jsonb_set(
    jsonb_set(
        definition,
        '{fields}',
        coalesce((
            select jsonb_agg(
                case when field->>'key' = 'authMode' then
                    field || jsonb_build_object(
                        'options', jsonb_build_array('API_KEY_AND_ORG_TOKEN'),
                        'defaultValue', 'API_KEY_AND_ORG_TOKEN',
                        'placeholder', 'Cargoes requests require both the API key and organization token.'
                    )
                else field end
                order by ordinality
            )
            from jsonb_array_elements(definition->'fields') with ordinality as item(field, ordinality)
        ), '[]'::jsonb),
        true
    ),
    '{credentialFields}',
    coalesce((
        select jsonb_agg(
            field || jsonb_build_object(
                'required', true,
                'placeholder', case field->>'key'
                    when 'apiKey' then 'Required value sent as X-DPW-ApiKey.'
                    when 'orgToken' then 'Required value sent as X-DPW-Org-Token.'
                    else field->>'placeholder'
                end
            )
            order by ordinality
        )
        from jsonb_array_elements(definition->'credentialFields') with ordinality as item(field, ordinality)
    ), '[]'::jsonb),
    true
), '{schemaVersion}', '2'::jsonb, true), updated_at = now()
where definition->>'displayName' = 'Cargoes Flow MCP';
