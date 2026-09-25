package dev.agentstudio.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/observability")
class ObservabilityController {
    private static final Map<String, Duration> RANGES = Map.of(
            "1m", Duration.ofMinutes(1),
            "15m", Duration.ofMinutes(15),
            "1h", Duration.ofHours(1),
            "6h", Duration.ofHours(6),
            "24h", Duration.ofDays(1),
            "7d", Duration.ofDays(7),
            "30d", Duration.ofDays(30));
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    ObservabilityController(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/summary")
    Summary summary(@RequestHeader("X-Tenant-Id") String tenant,
                    @RequestParam(defaultValue = "24h") String range) {
        String t = required(tenant);
        Duration duration = RANGES.get(range);
        if (duration == null) throw new IllegalArgumentException("Unsupported observability range: " + range);
        Instant from = Instant.now().minus(duration);
        Timestamp since = Timestamp.from(from);
        Totals totals = jdbc.sql("""
                select count(*) filter(where status in ('CREATED','RUNNING','WAITING','WAITING_APPROVAL')),
                       count(*) filter(where created_at>=? and status='COMPLETED'),
                       count(*) filter(where created_at>=? and status='FAILED'),
                       count(*) filter(where created_at>=? and status='CANCELLED'),
                       count(*) filter(where created_at>=?),
                       coalesce(avg(extract(epoch from (completed_at-started_at))*1000)
                           filter(where created_at>=? and completed_at is not null and started_at is not null),0),
                       coalesce(percentile_cont(0.95) within group(order by extract(epoch from (completed_at-started_at))*1000)
                           filter(where created_at>=? and completed_at is not null and started_at is not null),0)
                from runs where tenant_id=?
                """).params(since, since, since, since, since, since, t)
                .query((r, n) -> new Totals(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4),
                        r.getLong(5), r.getDouble(6), r.getDouble(7))).single();
        Usage usage = jdbc.sql("""
                select coalesce(sum((attributes->>'inputTokens')::bigint),0),
                       coalesce(sum((attributes->>'outputTokens')::bigint),0),
                       coalesce(sum((attributes->>'modelCalls')::bigint),0),
                       coalesce(sum((attributes->>'costMicros')::bigint),0)
                from run_events where tenant_id=? and event_type='usage.recorded' and occurred_at>=?
                """).params(t, since).query((r, n) -> new Usage(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4))).single();
        ToolMetrics tools = jdbc.sql("""
                select count(*),count(*) filter(where coalesce((attributes->>'cacheHit')::boolean,false)),
                       count(*) filter(where attributes->>'transport'='HTTP'),count(distinct attributes->>'capability')
                from run_events where tenant_id=? and event_type='tool.completed' and occurred_at>=?
                """).params(t, since).query((r, n) -> new ToolMetrics(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4))).single();
        List<Breakdown> models = jdbc.sql("""
                select coalesce(nullif(attributes->>'provider',''),'UNSPECIFIED')||' / '||coalesce(nullif(attributes->>'model',''),'unknown'),count(*)
                from run_events where tenant_id=? and event_type='model.completed' and occurred_at>=?
                group by 1 order by 2 desc limit 10
                """).params(t, since).query((r, n) -> new Breakdown(r.getString(1), r.getLong(2))).list();
        List<Breakdown> capabilities = jdbc.sql("""
                select coalesce(nullif(attributes->>'capability',''),'unspecified'),count(*)
                from run_events where tenant_id=? and event_type='tool.completed' and occurred_at>=?
                group by 1 order by 2 desc limit 12
                """).params(t, since).query((r, n) -> new Breakdown(r.getString(1), r.getLong(2))).list();
        List<Breakdown> transports = jdbc.sql("""
                select coalesce(nullif(attributes->>'transport',''),'UNSPECIFIED'),count(*)
                from run_events where tenant_id=? and event_type='tool.completed' and occurred_at>=?
                group by 1 order by 2 desc
                """).params(t, since).query((r, n) -> new Breakdown(r.getString(1), r.getLong(2))).list();
        List<Failure> failures = jdbc.sql("""
                select run_id,agent_id,agent_version,completed_at,error from runs
                where tenant_id=? and status='FAILED' and created_at>=? order by completed_at desc nulls last limit 10
                """).params(t, since).query((r, n) -> new Failure(r.getString(1), r.getString(2), r.getString(3),
                        r.getTimestamp(4) == null ? null : r.getTimestamp(4).toInstant(), r.getString(5))).list();
        List<Activity> activity = jdbc.sql("""
                select e.run_id,r.agent_id,r.agent_version,r.status,e.event_type,e.occurred_at,e.attributes::text
                from run_events e join runs r on r.tenant_id=e.tenant_id and r.run_id=e.run_id
                where e.tenant_id=? and e.occurred_at>=? and e.event_type in
                    ('execution.dispatched','mcp.pipeline.started','mcp.pipeline.completed','tool.completed','model.completed','run.failed','run.completed','run.cancelled')
                order by e.occurred_at desc limit 60
                """).params(t, since).query((r, n) -> new Activity(r.getString(1), r.getString(2), r.getString(3),
                        r.getString(4), r.getString(5), r.getTimestamp(6).toInstant(), map(r.getString(7)))).list();
        return new Summary(Instant.now(), from, range, totals, usage, tools, models, capabilities, transports, failures, activity);
    }

    private String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("X-Tenant-Id is required");
        return value;
    }

    private Map<String, Object> map(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return Map.of(); }
    }

    record Totals(long activeRuns, long completedRuns, long failedRuns, long cancelledRuns, long totalRuns,
                  double averageDurationMs, double p95DurationMs) {}
    record Usage(long inputTokens, long outputTokens, long modelCalls, long costMicros) {}
    record ToolMetrics(long calls, long cacheHits, long remoteCalls, long capabilities) {}
    record Breakdown(String name, long count) {}
    record Failure(String runId, String agentId, String agentVersion, Instant occurredAt, String error) {}
    record Activity(String runId, String agentId, String agentVersion, String runStatus, String eventType,
                    Instant occurredAt, Map<String, Object> attributes) {}
    record Summary(Instant generatedAt, Instant from, String range, Totals totals, Usage usage, ToolMetrics tools,
                   List<Breakdown> models, List<Breakdown> capabilities, List<Breakdown> transports,
                   List<Failure> failures, List<Activity> activity) {}
}
