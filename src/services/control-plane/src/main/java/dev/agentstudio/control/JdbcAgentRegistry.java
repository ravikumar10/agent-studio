package dev.agentstudio.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentstudio.domain.AgentDefinition;
import dev.agentstudio.domain.AgentVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAgentRegistry implements AgentRegistry {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public JdbcAgentRegistry(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public AgentDefinition create(AgentDefinition a) {
        jdbc.sql("insert into agents(tenant_id,id,display_name,description,owner_team,tags,interaction_mode,topology,trigger_mode,status,created_at,updated_at) values(?,?,?,?,?,?::jsonb,?,?,?,?,?,?)")
                .params(a.tenantId(), a.id(), a.displayName(), a.description(), a.ownerTeam(), write(a.tags()), a.interactionMode().name(), a.topology().name(), a.triggerMode().name(), a.status().name(), java.sql.Timestamp.from(a.createdAt()), java.sql.Timestamp.from(a.updatedAt())).update();
        return a;
    }
    @Override public List<AgentDefinition> list(String tenantId) {
        return jdbc.sql("select * from agents where tenant_id=? and status<>'ARCHIVED' order by id").param(tenantId).query(this::agent).list();
    }
    @Override public AgentDefinition update(AgentDefinition a) {
        int changed=jdbc.sql("update agents set display_name=?,description=?,owner_team=?,tags=?::jsonb,interaction_mode=?,topology=?,trigger_mode=?,updated_at=? where tenant_id=? and id=?")
                .params(a.displayName(),a.description(),a.ownerTeam(),write(a.tags()),a.interactionMode().name(),a.topology().name(),a.triggerMode().name(),java.sql.Timestamp.from(a.updatedAt()),a.tenantId(),a.id()).update();
        if(changed!=1) throw new IllegalArgumentException("agent not found");
        return a;
    }
    @Override public AgentVersion createVersion(AgentVersion v) {
        jdbc.sql("insert into agent_versions(tenant_id,agent_id,version,spec,lifecycle,checksum,created_at) values(?,?,?,?::jsonb,?,?,?)")
                .params(v.tenantId(), v.agentId(), v.version(), write(v), v.lifecycle().name(), v.checksum(), java.sql.Timestamp.from(v.createdAt())).update();
        return v;
    }
    @Override public List<AgentVersion> versions(String tenantId, String agentId) {
        return jdbc.sql("select spec,lifecycle from agent_versions where tenant_id=? and agent_id=? order by created_at desc")
                .params(tenantId, agentId).query((rs, n) -> readVersion(rs.getString("spec"), rs.getString("lifecycle"))).list();
    }
    @Override @Transactional public AgentVersion transition(String tenantId, String agentId, String version, AgentVersion.Lifecycle target) {
        AgentVersion current = jdbc.sql("select spec,lifecycle from agent_versions where tenant_id=? and agent_id=? and version=? for update")
                .params(tenantId, agentId, version).query((rs, n) -> readVersion(rs.getString("spec"), rs.getString("lifecycle"))).single();
        AgentVersion next = current.transitionTo(target);
        jdbc.sql("update agent_versions set spec=?::jsonb,lifecycle=? where tenant_id=? and agent_id=? and version=?")
                .params(write(next), target.name(), tenantId, agentId, version).update();
        if (target == AgentVersion.Lifecycle.ACTIVE) {
            jdbc.sql("insert into agent_release_state(tenant_id,agent_id,active_version,updated_at) values(?,?,?,now()) on conflict(tenant_id,agent_id) do update set active_version=excluded.active_version,updated_at=excluded.updated_at")
                    .params(tenantId, agentId, version).update();
        }
        return next;
    }
    private AgentDefinition agent(ResultSet rs, int row) throws SQLException {
        return new AgentDefinition(rs.getString("tenant_id"), rs.getString("id"), rs.getString("display_name"), rs.getString("description"), rs.getString("owner_team"), readSet(rs.getString("tags")), AgentDefinition.InteractionMode.valueOf(rs.getString("interaction_mode")), AgentDefinition.Topology.valueOf(rs.getString("topology")), AgentDefinition.TriggerMode.valueOf(rs.getString("trigger_mode")), AgentDefinition.Status.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private AgentVersion readVersion(String value, String lifecycle) { try { AgentVersion v = json.readValue(value, AgentVersion.class); return lifecycle.equals(v.lifecycle().name()) ? v : v.transitionTo(AgentVersion.Lifecycle.valueOf(lifecycle)); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private Set<String> readSet(String value) { try { return Arrays.stream(json.readValue(value, String[].class)).collect(Collectors.toUnmodifiableSet()); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
}
