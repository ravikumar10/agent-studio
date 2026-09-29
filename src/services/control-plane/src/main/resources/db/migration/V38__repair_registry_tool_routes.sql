update capability_provider_bindings
set remote_operation_name = '/tools/' || capability_id,
    updated_at = now()
where compatibility->>'requiresDeployment' = 'true'
  and remote_operation_name not like '/tools/%';
