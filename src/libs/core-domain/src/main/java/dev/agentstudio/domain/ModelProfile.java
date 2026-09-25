package dev.agentstudio.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.Map;

public record ModelProfile(String tenantId, String id, QualityTier qualityTier, LatencyTier latencyTier,
        Integer maxInputTokens, Integer maxOutputTokens, Set<Feature> requiredFeatures,
        FallbackPolicy fallbackPolicy, BigDecimal maxCostPerCall, String connectionId, String modelId,
        Map<String,Object> generationParameters) {
    public enum QualityTier { FAST, BALANCED, HIGH, MAX }
    public enum LatencyTier { LOW, NORMAL, BATCH }
    public enum Feature { TOOL_CALLING, STRUCTURED_OUTPUT, VISION, AUDIO, LONG_CONTEXT }
    public enum FallbackPolicy { NONE, SAME_TIER, ALLOW_LOWER_TIER }
    public ModelProfile {
        if (tenantId == null || tenantId.isBlank() || id == null || id.isBlank()) throw new DomainValidationException("tenantId and id are required");
        qualityTier = Objects.requireNonNull(qualityTier, "qualityTier"); latencyTier = Objects.requireNonNull(latencyTier, "latencyTier");
        if (maxInputTokens != null && maxInputTokens < 1 || maxOutputTokens != null && maxOutputTokens < 1) throw new DomainValidationException("token limits must be positive");
        requiredFeatures = Set.copyOf(Objects.requireNonNullElse(requiredFeatures, Set.of()));
        fallbackPolicy = Objects.requireNonNullElse(fallbackPolicy, FallbackPolicy.NONE);
        if (maxCostPerCall != null && maxCostPerCall.signum() < 0) throw new DomainValidationException("maxCostPerCall cannot be negative");
        generationParameters = Map.copyOf(Objects.requireNonNullElse(generationParameters, Map.of()));
    }
}
