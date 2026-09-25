package dev.agentstudio.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record AgentVersion(
        String tenantId, String agentId, String version, RuntimeType runtimeType, HostingMode hostingMode,
        String artifactRef, String remoteEndpointRef, Set<String> capabilitiesProvided,
        Set<String> toolCapabilitiesRequired, Set<String> agentCapabilitiesRequired, String modelProfile,
        String promptRef, String inputSchemaRef, String outputSchemaRef, String executionPolicyRef,
        String securityPolicyRef, String checksum, Lifecycle lifecycle, Instant createdAt) {
    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9.-]{2,127}$");
    private static final Pattern SEMVER = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?$");
    public enum RuntimeType { CONFIG, EMBABEL, REMOTE_HTTP, REMOTE_A2A }
    public enum HostingMode { PLATFORM, CLIENT, EXTERNAL }
    public enum Lifecycle { CREATED, VALIDATED, CANARY, ACTIVE, RETIRED, REVOKED }

    public AgentVersion {
        tenantId = required(tenantId, "tenantId");
        agentId = matching(agentId, "agentId", ID);
        version = matching(version, "version", SEMVER);
        runtimeType = Objects.requireNonNull(runtimeType, "runtimeType");
        hostingMode = Objects.requireNonNull(hostingMode, "hostingMode");
        if (hostingMode == HostingMode.EXTERNAL && blank(remoteEndpointRef))
            throw new DomainValidationException("remoteEndpointRef is required for EXTERNAL hosting");
        capabilitiesProvided = immutable(capabilitiesProvided);
        toolCapabilitiesRequired = immutable(toolCapabilitiesRequired);
        agentCapabilitiesRequired = immutable(agentCapabilitiesRequired);
        modelProfile = required(modelProfile, "modelProfile");
        inputSchemaRef = required(inputSchemaRef, "inputSchemaRef");
        outputSchemaRef = required(outputSchemaRef, "outputSchemaRef");
        lifecycle = Objects.requireNonNullElse(lifecycle, Lifecycle.CREATED);
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
    }

    public AgentVersion transitionTo(Lifecycle target) {
        boolean allowed = switch (lifecycle) {
            case CREATED -> target == Lifecycle.VALIDATED || target == Lifecycle.REVOKED;
            case VALIDATED -> target == Lifecycle.CANARY || target == Lifecycle.ACTIVE || target == Lifecycle.REVOKED;
            case CANARY -> target == Lifecycle.ACTIVE || target == Lifecycle.RETIRED || target == Lifecycle.REVOKED;
            case ACTIVE -> target == Lifecycle.RETIRED || target == Lifecycle.REVOKED;
            case RETIRED, REVOKED -> false;
        };
        if (!allowed) throw new DomainValidationException("Invalid lifecycle transition: " + lifecycle + " -> " + target);
        return new AgentVersion(tenantId, agentId, version, runtimeType, hostingMode, artifactRef, remoteEndpointRef,
                capabilitiesProvided, toolCapabilitiesRequired, agentCapabilitiesRequired, modelProfile, promptRef,
                inputSchemaRef, outputSchemaRef, executionPolicyRef, securityPolicyRef, checksum, target, createdAt);
    }

    private static Set<String> immutable(Set<String> value) { return Set.copyOf(Objects.requireNonNullElse(value, Set.of())); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String required(String value, String name) { if (blank(value)) throw new DomainValidationException(name + " is required"); return value; }
    private static String matching(String value, String name, Pattern pattern) { required(value, name); if (!pattern.matcher(value).matches()) throw new DomainValidationException(name + " has invalid format"); return value; }
}
