-- Pricing remains profile configuration. Seed only known profiles whose rates were
-- omitted by older Studio versions; never overwrite an explicitly configured rate.
-- Rates are USD per one million tokens and are source-dated for auditability.
update model_profiles
set spec = spec || jsonb_build_object(
  'generationParameters',
  coalesce(spec->'generationParameters', '{}'::jsonb) || jsonb_build_object(
    'inputCostPerMillionUsd', 2.00,
    'outputCostPerMillionUsd', 8.00,
    'pricingSource', 'https://developers.openai.com/api/docs/models/gpt-4.1',
    'pricingAsOf', '2026-09-30'
  )
)
where spec->>'modelId' = 'gpt-4.1'
  and not (coalesce(spec->'generationParameters', '{}'::jsonb) ? 'inputCostPerMillionUsd')
  and not (coalesce(spec->'generationParameters', '{}'::jsonb) ? 'outputCostPerMillionUsd');

update model_profiles
set spec = spec || jsonb_build_object(
  'generationParameters',
  coalesce(spec->'generationParameters', '{}'::jsonb) || jsonb_build_object(
    'inputCostPerMillionUsd', 3.00,
    'outputCostPerMillionUsd', 15.00,
    'pricingSource', 'https://platform.claude.com/docs/en/models/sonnet-4-6/overview',
    'pricingAsOf', '2026-09-30'
  )
)
where spec->>'modelId' = 'claude-sonnet-4-6'
  and not (coalesce(spec->'generationParameters', '{}'::jsonb) ? 'inputCostPerMillionUsd')
  and not (coalesce(spec->'generationParameters', '{}'::jsonb) ? 'outputCostPerMillionUsd');

-- Historical usage already contains the combined planner/synthesis token counts.
-- Recalculate only zero-cost events backed by a now-priced logical model profile.
update run_events event
set attributes = jsonb_set(
  event.attributes,
  '{costMicros}',
  to_jsonb(round(
    coalesce((event.attributes->>'inputTokens')::numeric, 0)
      * coalesce((profile.spec->'generationParameters'->>'inputCostPerMillionUsd')::numeric, 0)
    + coalesce((event.attributes->>'outputTokens')::numeric, 0)
      * coalesce((profile.spec->'generationParameters'->>'outputCostPerMillionUsd')::numeric, 0)
  )::bigint),
  true
)
from runs run
join agent_versions version
  on version.tenant_id = run.tenant_id
 and version.agent_id = run.agent_id
 and version.version = run.agent_version
join model_profiles profile
  on profile.tenant_id = run.tenant_id
 and profile.profile_id = version.spec->>'modelProfile'
where event.tenant_id = run.tenant_id
  and event.run_id = run.run_id
  and event.event_type = 'usage.recorded'
  and coalesce((event.attributes->>'costMicros')::bigint, 0) = 0
  and coalesce((event.attributes->>'modelCalls')::bigint, 0) > 0
  and profile.spec->'generationParameters'->>'inputCostPerMillionUsd' is not null
  and profile.spec->'generationParameters'->>'outputCostPerMillionUsd' is not null;
