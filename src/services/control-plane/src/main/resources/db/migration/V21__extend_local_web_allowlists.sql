update capability_providers
set configuration = jsonb_set(
        configuration,
        '{allowedHosts}',
        to_jsonb('example.com,www.example.com,wikipedia.org,en.wikipedia.org'::text),
        true
    ),
    updated_at = now()
where tenant_id = 'local-development'
  and provider_id in ('local-http-mcp', 'local-browser-mcp');
