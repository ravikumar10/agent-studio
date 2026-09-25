package dev.agentstudio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandardAgentResponseComposerTest {
    @Test
    @SuppressWarnings("unchecked")
    void keepsLlmNarrativeAndAllMcpChartsInOneOrderedResponse() {
        Map<String,Object> chartA=Map.of("chartType","bar","title","Sales","spec",Map.of("mark","bar"));
        Map<String,Object> chartB=Map.of("chartType","line","title","Trend","spec",Map.of("mark","line"));
        var step=new CapabilityPipeline.Step(2,"chart.generate","chart-mcp","IN_PROCESS",
                Map.of("charts",List.of(chartA,chartB),"downloadFormats",List.of("PNG","SVG")),false);

        Map<String,Object> response=new StandardAgentResponseComposer().compose("LLM summary",List.of(step),Map.of("model","test"));
        List<Map<String,Object>> blocks=(List<Map<String,Object>>)response.get("blocks");

        assertThat(response).containsEntry("schemaVersion","1.1").containsEntry("role","assistant");
        assertThat(blocks).extracting(block->block.get("type")).containsExactly("markdown","chart","chart");
        assertThat(blocks.get(0)).containsEntry("content","LLM summary");
        assertThat((Map<String,Object>)response.get("metadata")).containsEntry("chartCount",2).containsEntry("source","LLM_AND_MCP");
    }

    @Test
    @SuppressWarnings("unchecked")
    void includesGenericToolArtifactsWithoutEmbeddingExecutionTelemetry() {
        var step=new CapabilityPipeline.Step(1,"image.generate","image-mcp","HTTP",
                Map.of("artifacts",List.of(Map.of("type","image","content",Map.of("url","/files/chart.png","alt","Forecast")))),false);
        Map<String,Object> response=new StandardAgentResponseComposer().compose("Forecast attached",List.of(step),Map.of());
        List<Map<String,Object>> blocks=(List<Map<String,Object>>)response.get("blocks");
        assertThat(blocks).extracting(block->block.get("type")).containsExactly("markdown","image");
        assertThat(response.toString()).doesNotContain("image-mcp","cacheHit","iteration");
    }
}
