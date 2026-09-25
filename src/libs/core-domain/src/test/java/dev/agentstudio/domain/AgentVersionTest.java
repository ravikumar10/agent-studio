package dev.agentstudio.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentVersionTest {
    private AgentVersion created() {
        return new AgentVersion("tenant-a", "shipment-investigator", "1.0.0", AgentVersion.RuntimeType.EMBABEL,
                AgentVersion.HostingMode.PLATFORM, "oci://agents/shipment:1.0.0", null, Set.of("shipment.delay.investigate"),
                Set.of("shipment.lookup"), Set.of("route.analyze"), "reasoning-balanced", "prompt://shipment/1",
                "schema://input/1", "schema://output/1", null, null, "sha256:abc", AgentVersion.Lifecycle.CREATED, Instant.EPOCH);
    }
    @Test void copiesCollectionsAndSupportsValidLifecycle() {
        AgentVersion validated = created().transitionTo(AgentVersion.Lifecycle.VALIDATED);
        assertEquals(AgentVersion.Lifecycle.VALIDATED, validated.lifecycle());
        assertThrows(UnsupportedOperationException.class, () -> validated.toolCapabilitiesRequired().add("other"));
    }
    @Test void rejectsSkippingLifecycle() {
        assertThrows(DomainValidationException.class, () -> created().transitionTo(AgentVersion.Lifecycle.ACTIVE));
    }
    @Test void validatesSemanticVersion() {
        assertThrows(DomainValidationException.class, () -> new AgentVersion("t", "valid-agent", "latest", AgentVersion.RuntimeType.CONFIG,
                AgentVersion.HostingMode.PLATFORM, null, null, Set.of(), Set.of(), Set.of(), "profile", null,
                "schema://in", "schema://out", null, null, null, null, null));
    }
}
