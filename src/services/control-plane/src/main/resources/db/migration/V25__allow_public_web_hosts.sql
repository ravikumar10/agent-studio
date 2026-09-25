update capability_providers
set configuration = jsonb_set(configuration, '{allowedHosts}', '"*"'::jsonb, true),
    updated_at = now()
where integration_kind in ('HTTP_API','HEADLESS_BROWSER')
  and coalesce(configuration ->> 'allowedHosts', '') in (
      '',
      'example.com,www.example.com',
      'example.com,www.example.com,wikipedia.org,en.wikipedia.org'
  );

update capabilities
set spec = jsonb_set(spec, '{description}', to_jsonb('Access public HTTP(S) resources while blocking local, private, link-local, and metadata network targets'::text), true)
where tenant_id = 'local-development'
  and capability_id in ('web.fetch','http.request','browser.navigate','browser.extract','browser.act');
