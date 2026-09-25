package dev.agentstudio.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.RunModels.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RunStore {
    private final JdbcClient jdbc; private final ObjectMapper json;
    public RunStore(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public AgentVersion resolve(String tenant, String agentId, String requestedVersion) {
        String version = requestedVersion;
        if (version == null || version.isBlank()) version = jdbc.sql("select active_version from agent_release_state where tenant_id=? and agent_id=?").params(tenant,agentId).query(String.class).single();
        return jdbc.sql("select spec from agent_versions where tenant_id=? and agent_id=? and version=? and lifecycle in ('ACTIVE','CANARY')")
                .params(tenant,agentId,version).query((rs,n)->read(rs.getString(1),AgentVersion.class)).single();
    }
    public void create(RunView r) { jdbc.sql("insert into runs(tenant_id,run_id,agent_id,agent_version,status,created_at) values(?,?,?,?,?,?)").params(r.tenantId(),r.runId(),r.agentId(),r.agentVersion(),r.status().name(),java.sql.Timestamp.from(r.createdAt())).update(); }
    public void running(String tenant,String id) { jdbc.sql("update runs set status='RUNNING',started_at=now() where tenant_id=? and run_id=? and status='CREATED'").params(tenant,id).update(); }
    public void complete(String tenant,String id,Map<String,Object> output) { jdbc.sql("update runs set status='COMPLETED',output=?::jsonb,completed_at=now() where tenant_id=? and run_id=? and status='RUNNING'").params(write(output),tenant,id).update(); }
    public void fail(String tenant,String id,String error) { jdbc.sql("update runs set status='FAILED',error=?,completed_at=now() where tenant_id=? and run_id=? and status in ('CREATED','RUNNING')").params(error,tenant,id).update(); }
    public boolean cancel(String tenant,String id) { return jdbc.sql("update runs set status='CANCELLED',completed_at=now() where tenant_id=? and run_id=? and status in ('CREATED','RUNNING','WAITING','WAITING_APPROVAL')").params(tenant,id).update()>0; }
    public RunView get(String tenant,String id) { return jdbc.sql("select * from runs where tenant_id=? and run_id=?").params(tenant,id).query((rs,n)->new RunView(rs.getString("run_id"),rs.getString("tenant_id"),rs.getString("agent_id"),rs.getString("agent_version"),Status.valueOf(rs.getString("status")),instant(rs,"created_at"),instant(rs,"started_at"),instant(rs,"completed_at"),map(rs.getString("output")),rs.getString("error"))).single(); }
    public List<RunView> list(String tenant) { return jdbc.sql("select * from runs where tenant_id=? order by created_at desc limit 100").param(tenant).query((rs,n)->new RunView(rs.getString("run_id"),rs.getString("tenant_id"),rs.getString("agent_id"),rs.getString("agent_version"),Status.valueOf(rs.getString("status")),instant(rs,"created_at"),instant(rs,"started_at"),instant(rs,"completed_at"),map(rs.getString("output")),rs.getString("error"))).list(); }
    public void event(String tenant,String run,String type,Map<String,Object> attrs) { jdbc.sql("insert into run_events(tenant_id,run_id,event_type,attributes,occurred_at) values(?,?,?,?::jsonb,now())").params(tenant,run,type,write(attrs)).update(); }
    public List<EventView> events(String tenant,String run) { return jdbc.sql("select sequence,event_type,occurred_at,attributes from run_events where tenant_id=? and run_id=? order by sequence").params(tenant,run).query((rs,n)->new EventView(rs.getLong(1),rs.getString(2),rs.getTimestamp(3).toInstant(),map(rs.getString(4)))).list(); }
    private Instant instant(java.sql.ResultSet rs,String col) throws java.sql.SQLException { var t=rs.getTimestamp(col); return t==null?null:t.toInstant(); }
    private String write(Object o) { try{return json.writeValueAsString(o);}catch(JsonProcessingException e){throw new IllegalStateException(e);} }
    private <T>T read(String s,Class<T> t){try{return json.readValue(s,t);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private Map<String,Object> map(String s){if(s==null)return Map.of();try{return json.readValue(s,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
