package dev.agentstudio.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/observability")
class ObservabilityController {
    private final JdbcClient jdbc; private final ObjectMapper json;
    ObservabilityController(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @GetMapping("/summary")
    Summary summary(@RequestHeader("X-Tenant-Id")String tenant){
        String t=required(tenant);
        Totals totals=jdbc.sql("""
                select count(*) filter(where status in ('CREATED','RUNNING','WAITING','WAITING_APPROVAL')) active_runs,
                       count(*) filter(where status='COMPLETED') completed_runs,
                       count(*) filter(where status='FAILED') failed_runs
                from runs where tenant_id=? and created_at>=now()-interval '24 hours'
                """).param(t).query((r,n)->new Totals(r.getLong(1),r.getLong(2),r.getLong(3))).single();
        Usage usage=jdbc.sql("""
                select coalesce(sum((attributes->>'inputTokens')::bigint),0),
                       coalesce(sum((attributes->>'outputTokens')::bigint),0),
                       coalesce(sum((attributes->>'modelCalls')::bigint),0),
                       coalesce(sum((attributes->>'costMicros')::bigint),0)
                from run_events where tenant_id=? and event_type='usage.recorded' and occurred_at>=now()-interval '24 hours'
                """).param(t).query((r,n)->new Usage(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4))).single();
        ToolMetrics tools=jdbc.sql("""
                select count(*),count(*) filter(where coalesce((attributes->>'cacheHit')::boolean,false)),
                       count(*) filter(where attributes->>'transport'='HTTP'),count(distinct attributes->>'capability')
                from run_events where tenant_id=? and event_type='tool.completed' and occurred_at>=now()-interval '24 hours'
                """).param(t).query((r,n)->new ToolMetrics(r.getLong(1),r.getLong(2),r.getLong(3),r.getLong(4))).single();
        List<Activity> activity=jdbc.sql("""
                select e.run_id,r.agent_id,r.agent_version,r.status,e.event_type,e.occurred_at,e.attributes::text
                from run_events e join runs r on r.tenant_id=e.tenant_id and r.run_id=e.run_id
                where e.tenant_id=? and e.event_type in ('execution.dispatched','mcp.pipeline.started','tool.completed','model.completed','run.failed','run.completed','run.cancelled')
                order by e.occurred_at desc limit 80
                """).param(t).query((r,n)->new Activity(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6).toInstant(),map(r.getString(7)))).list();
        return new Summary(Instant.now(),totals,usage,tools,activity);
    }
    private String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("X-Tenant-Id is required");return value;}
    private Map<String,Object> map(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();}}
    record Totals(long activeRuns,long completedRuns,long failedRuns){}
    record Usage(long inputTokens,long outputTokens,long modelCalls,long costMicros){}
    record ToolMetrics(long calls,long cacheHits,long remoteCalls,long capabilities){}
    record Activity(String runId,String agentId,String agentVersion,String runStatus,String eventType,Instant occurredAt,Map<String,Object> attributes){}
    record Summary(Instant generatedAt,Totals totals,Usage usage,ToolMetrics tools,List<Activity> activity){}
}
