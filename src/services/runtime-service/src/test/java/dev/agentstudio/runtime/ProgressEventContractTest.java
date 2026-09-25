package dev.agentstudio.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgressEventContractTest {
    @Test @SuppressWarnings("unchecked") void addsSafeSemanticProgressWithoutRemovingTelemetry(){
        Map<String,Object> decorated=new ProgressEventContract().decorate("tool.completed",Map.of("capability","postgres.query-readonly","cacheHit",false));
        assertThat(decorated).containsEntry("cacheHit",false);
        assertThat((Map<String,Object>)decorated.get("progress")).containsEntry("kind","agent.progress").containsEntry("stage","TOOLS").containsEntry("status","COMPLETED").containsEntry("title","Postgres query readonly completed");
    }
}
