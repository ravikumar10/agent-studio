package dev.agentstudio.control;

import dev.agentstudio.domain.AgentDefinition;
import dev.agentstudio.domain.AgentVersion;
import java.util.List;

public interface AgentRegistry {
    AgentDefinition create(AgentDefinition agent);
    AgentDefinition update(AgentDefinition agent);
    List<AgentDefinition> list(String tenantId);
    AgentVersion createVersion(AgentVersion version);
    List<AgentVersion> versions(String tenantId, String agentId);
    AgentVersion transition(String tenantId, String agentId, String version, AgentVersion.Lifecycle lifecycle);
}
