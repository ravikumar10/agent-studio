package dev.agentstudio.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentstudio.domain.Capability;
import dev.agentstudio.domain.ModelProfile;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGovernanceCatalog implements GovernanceCatalog {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public JdbcGovernanceCatalog(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public Capability createCapability(Capability value) {
        jdbc.sql("insert into capabilities(tenant_id,capability_id,spec) values(?,?,?::jsonb)")
                .params(value.tenantId(), value.capabilityId(), write(value)).update();
        return value;
    }
    @Override public List<Capability> capabilities(String tenantId) {
        return jdbc.sql("select spec from capabilities where tenant_id=? order by capability_id").param(tenantId)
                .query((rs, row) -> read(rs.getString("spec"), Capability.class)).list();
    }
    @Override public ModelProfile createModelProfile(ModelProfile value) {
        jdbc.sql("insert into model_profiles(tenant_id,profile_id,spec) values(?,?,?::jsonb)")
                .params(value.tenantId(), value.id(), write(value)).update();
        return value;
    }
    @Override public List<ModelProfile> modelProfiles(String tenantId) {
        return jdbc.sql("select spec from model_profiles where tenant_id=? order by profile_id").param(tenantId)
                .query((rs, row) -> read(rs.getString("spec"), ModelProfile.class)).list();
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
}
