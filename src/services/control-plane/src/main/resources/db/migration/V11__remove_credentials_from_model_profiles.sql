-- Credentials belong only in encrypted user_secrets/model_connections. Repair any legacy
-- profile where a credential was accidentally pasted into the provider model ID field.
update model_profiles
set spec = jsonb_set(spec, '{modelId}', 'null'::jsonb, true)
where coalesce(spec->>'modelId','') ~* '^(sk-|bearer\s+|api[_-]?key)';

