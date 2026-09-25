package dev.agentstudio.runtime;

import dev.agentstudio.runtime.api.ExecutionContext;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public final class RunModels {
    private RunModels() {}
    public enum Status { CREATED, RUNNING, WAITING, WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED, TIMED_OUT }
    public record StartRunRequest(String agentId, String version, Map<String,Object> input, String subjectId,
            Set<String> scopes, Instant deadline, ExecutionContext.Budget budget, boolean async) {}
    public record RunView(String runId, String tenantId, String agentId, String agentVersion, Status status,
            Instant createdAt, Instant startedAt, Instant completedAt, Map<String,Object> output, String error) {}
    public record EventView(long sequence, String type, Instant occurredAt, Map<String,Object> attributes) {}
}
