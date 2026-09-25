package dev.agentstudio.slack;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

class SlackMcpApplicationTest {
    @Test void applicationTypeIsAvailable(){assertThat(SlackMcpApplication.class).isNotNull();}
    @Test void rendersAgentStudioChartAsPng(){
        byte[] image=new ChartPngRenderer().render(Map.of("chartType","bar","title","Inventory","spec",Map.of("title","Inventory","data",Map.of("values",List.of(Map.of("name","Keyboard","stock",8),Map.of("name","Mouse","stock",15))),"encoding",Map.of("x",Map.of("field","name"),"y",Map.of("field","stock")))));
        assertThat(image).hasSizeGreaterThan(1000);assertThat(image[0]).isEqualTo((byte)0x89);assertThat(image[1]).isEqualTo((byte)0x50);
    }
}
