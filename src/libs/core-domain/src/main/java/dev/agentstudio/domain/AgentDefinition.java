package dev.agentstudio.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record AgentDefinition(
        String tenantId, String id, String displayName, String description,
        String ownerTeam, Set<String> tags, InteractionMode interactionMode, Topology topology,
        TriggerMode triggerMode, Status status, Instant createdAt, Instant updatedAt) {
    public enum Status { DRAFT, ACTIVE, ARCHIVED }
    public enum InteractionMode { TASK, CHAT, TASK_AND_CHAT }
    public enum Topology { SINGLE_AGENT, MULTI_AGENT }
    public enum TriggerMode { ON_DEMAND, SCHEDULED, EVENT_DRIVEN }

    public AgentDefinition {
        tenantId = required(tenantId, "tenantId");
        id = required(id, "id");
        displayName = required(displayName, "displayName");
        ownerTeam = required(ownerTeam, "ownerTeam");
        description = Objects.requireNonNullElse(description, "");
        tags = Set.copyOf(Objects.requireNonNullElse(tags, Set.of()));
        interactionMode = Objects.requireNonNullElse(interactionMode, InteractionMode.TASK_AND_CHAT);
        topology = Objects.requireNonNullElse(topology, Topology.SINGLE_AGENT);
        triggerMode = Objects.requireNonNullElse(triggerMode, TriggerMode.ON_DEMAND);
        status = Objects.requireNonNullElse(status, Status.DRAFT);
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        updatedAt = Objects.requireNonNullElse(updatedAt, createdAt);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new DomainValidationException(name + " is required");
        return value;
    }
}
