update capabilities
set spec=jsonb_set(spec,'{riskClass}','"LOW_RISK_WRITE"'::jsonb,false)
where tenant_id='local-development' and capability_id='kubernetes.apply-workload';

update capabilities
set spec=jsonb_set(spec,'{riskClass}','"REVERSIBLE_WRITE"'::jsonb,false)
where tenant_id='local-development' and capability_id='kubernetes.rollback-workload';
