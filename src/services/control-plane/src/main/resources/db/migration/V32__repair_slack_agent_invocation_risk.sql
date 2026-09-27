update capabilities
set spec=jsonb_set(spec,'{riskClass}','"PRIVILEGED"'::jsonb)
where capability_id='slack.agent.invoke'
  and spec->>'riskClass'='CONTROLLED_WRITE';
