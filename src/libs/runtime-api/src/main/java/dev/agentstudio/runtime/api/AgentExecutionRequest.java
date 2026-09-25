package dev.agentstudio.runtime.api;

import java.util.Map;
import java.util.Objects;

public record AgentExecutionRequest(String invocationId, Map<String, Object> input, Map<String, Object> constraints) {
    public AgentExecutionRequest {
        Objects.requireNonNull(invocationId);
        input = Map.copyOf(Objects.requireNonNullElse(input, Map.of()));
        constraints = Map.copyOf(Objects.requireNonNullElse(constraints, Map.of()));
    }
}
