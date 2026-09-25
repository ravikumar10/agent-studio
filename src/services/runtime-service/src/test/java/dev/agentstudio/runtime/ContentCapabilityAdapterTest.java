package dev.agentstudio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContentCapabilityAdapterTest {
    private final ContentCapabilityAdapter adapter = new ContentCapabilityAdapter(null, new ObjectMapper());

    @Test
    @SuppressWarnings("unchecked")
    void createsDownloadableGroupedChartFromComplexToolData() {
        Map<String, Object> result = (Map<String, Object>) adapter.invoke("tenant", "agent", "chart.generate", Map.of(
                "message", "Create a grouped bar chart",
                "previousResult", Map.of("rows", List.of(
                        Map.of("name", "Router", "price", 120, "stock", 14, "region", "west"),
                        Map.of("name", "Switch", "price", 80, "stock", 27, "region", "east")))));

        assertThat(result).containsEntry("generated", true).containsEntry("chartType", "grouped-bar");
        assertThat((List<String>) result.get("downloadFormats")).containsExactly("PNG", "SVG", "CSV", "VEGA_LITE_JSON");
        Map<String, Object> spec = (Map<String, Object>) result.get("spec");
        assertThat(spec).containsKeys("data", "transform", "encoding");
        assertThat(((Map<String, Object>) spec.get("data")).get("values")).asList().hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void limitsLargeDatasetsAndSeries() {
        List<Map<String, Object>> rows = java.util.stream.IntStream.range(0, 300)
                .mapToObj(index -> Map.<String, Object>of("name", "item-" + index, "value", index))
                .toList();
        Map<String, Object> result = (Map<String, Object>) adapter.invoke("tenant", "agent", "chart.generate",
                Map.of("message", "chart", "previousResult", rows));

        assertThat(result).containsEntry("rowCount", 250);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsMultipleChartsForOneAgentResponse() {
        Map<String, Object> result = (Map<String, Object>) adapter.invoke("tenant", "agent", "chart.generate", Map.of(
                "message", "Create multiple charts as a dashboard",
                "previousResult", List.of(
                        Map.of("name", "Router", "price", 120, "stock", 14),
                        Map.of("name", "Switch", "price", 80, "stock", 27))));

        assertThat(result).containsEntry("chartCount", 4);
        List<Map<String, Object>> charts = (List<Map<String, Object>>) result.get("charts");
        assertThat(charts).extracting(chart -> chart.get("chartType"))
                .containsExactly("grouped-bar", "line", "scatter", "donut");
        assertThat(charts).allSatisfy(chart -> assertThat(chart).containsKeys("id", "title", "spec"));
    }
}
