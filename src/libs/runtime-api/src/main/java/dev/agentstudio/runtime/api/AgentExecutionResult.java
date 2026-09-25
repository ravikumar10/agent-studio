package dev.agentstudio.runtime.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AgentExecutionResult(Status status, Map<String, Object> output, List<ArtifactRef> artifacts,
        List<SemanticEvent> events, Usage usage, String error) {
    public enum Status { COMPLETED, FAILED, WAITING_APPROVAL, CANCELLED }
    public AgentExecutionResult {
        Objects.requireNonNull(status); output = Map.copyOf(Objects.requireNonNullElse(output, Map.of()));
        artifacts = List.copyOf(Objects.requireNonNullElse(artifacts, List.of()));
        events = List.copyOf(Objects.requireNonNullElse(events, List.of()));
        usage = Objects.requireNonNullElse(usage, new Usage(0, 0, 0, 0));
    }
    public record ArtifactRef(String artifactId, String mediaType, String storageRef, String checksum) {}
    public record SemanticEvent(String type, Instant timestamp, Map<String, Object> attributes) {
        public SemanticEvent { attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of())); }
    }
    public record Usage(int inputTokens, int outputTokens, int modelCalls, long costMicros) {}
}
