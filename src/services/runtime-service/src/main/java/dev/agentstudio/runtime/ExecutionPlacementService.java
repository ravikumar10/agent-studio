package dev.agentstudio.runtime;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
class ExecutionPlacementService {
    enum Placement { AUTO, IN_PROCESS, DOCKER, KUBERNETES }
    private final JdbcClient jdbc;

    ExecutionPlacementService(JdbcClient jdbc) { this.jdbc = jdbc; }

    Placement resolve(String tenantId, String agentId, String version) {
        String value = jdbc.sql("select execution_placement from agent_runtime_configurations where tenant_id=? and agent_id=? and agent_version=?")
                .params(tenantId, agentId, version).query(String.class).optional().orElse("AUTO");
        return Placement.valueOf(value);
    }
}
