package dev.agentstudio.domain;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record Capability(String tenantId, String capabilityId, String displayName, String description, Kind kind,
                         String inputSchemaRef, String outputSchemaRef, RiskClass riskClass, String owner, Set<String> tags) {
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,191}$");
    public enum Kind { TOOL, AGENT, MODEL_SERVICE }
    public enum RiskClass { READ_ONLY, LOW_RISK_WRITE, REVERSIBLE_WRITE, IRREVERSIBLE_WRITE, PRIVILEGED }
    public Capability {
        required(tenantId, "tenantId"); required(capabilityId, "capabilityId");
        if (!ID.matcher(capabilityId).matches()) throw new DomainValidationException("capabilityId has invalid format");
        kind = Objects.requireNonNull(kind, "kind"); required(inputSchemaRef, "inputSchemaRef");
        required(outputSchemaRef, "outputSchemaRef"); riskClass = Objects.requireNonNull(riskClass, "riskClass");
        required(owner, "owner"); tags = Set.copyOf(Objects.requireNonNullElse(tags, Set.of()));
    }
    private static void required(String value, String name) { if (value == null || value.isBlank()) throw new DomainValidationException(name + " is required"); }
}
