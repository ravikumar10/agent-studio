package dev.agentstudio.runtime.api;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record ExecutionContext(String tenantId, String subjectId, Set<String> scopes, String runId,
        String taskId, Instant deadline, Budget budget, String dataClassification, int delegationDepth) {
    public ExecutionContext {
        Objects.requireNonNull(tenantId); Objects.requireNonNull(subjectId); Objects.requireNonNull(runId);
        scopes = Set.copyOf(Objects.requireNonNullElse(scopes, Set.of()));
        deadline = Objects.requireNonNullElseGet(deadline, () -> Instant.now().plusSeconds(300));
        budget = Objects.requireNonNullElse(budget, Budget.defaults());
        if (delegationDepth < 0) throw new IllegalArgumentException("delegationDepth cannot be negative");
    }
    public record Budget(int maxModelCalls, int maxToolCalls, int maxDelegations, int maxTokens, long maxCostMicros) {
        public Budget { if (maxModelCalls < 0 || maxToolCalls < 0 || maxDelegations < 0 || maxTokens < 0 || maxCostMicros < 0) throw new IllegalArgumentException("budget values cannot be negative"); }
        public static Budget defaults() { return new Budget(8, 16, 4, 32_000, 1_000_000); }
    }
}
