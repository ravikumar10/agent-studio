package dev.agentstudio.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ConfigRuntimeAdapterTest {
    @Test void removesUnverifiedSlackDeliveryClaimBeforeTheToolRuns() {
        String report="# Report\n\nUseful evidence.\n\n## Slack Delivery\n\nNo channel was configured.";
        assertEquals("# Report\n\nUseful evidence.",ConfigRuntimeAdapter.withoutUnverifiedDeliveryStatus(report));
    }

    @Test void preservesOrdinaryReportSections() {
        String report="# Report\n\n## Findings\n\nUseful evidence.";
        assertEquals(report,ConfigRuntimeAdapter.withoutUnverifiedDeliveryStatus(report));
    }
}
