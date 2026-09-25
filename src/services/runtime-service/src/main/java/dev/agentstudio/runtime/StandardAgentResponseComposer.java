package dev.agentstudio.runtime;

import java.util.*;
import org.springframework.stereotype.Component;

/** Builds the stable user-facing response document. Execution/tool telemetry stays outside this contract. */
@Component
class StandardAgentResponseComposer {
    Map<String,Object> compose(String narrative,List<CapabilityPipeline.Step> steps,Map<String,Object> metadata){
        List<Map<String,Object>> blocks=new ArrayList<>();
        blocks.add(Map.of("id","text-1","type","markdown","content",Objects.requireNonNullElse(narrative,"")));
        int chartIndex=0,artifactIndex=0;
        for(var step:steps){if(!(step.output() instanceof Map<?,?> output))continue;
            if("chart.generate".equals(step.capability())){Object collection=output.get("charts");
                if(collection instanceof Collection<?> charts){for(Object value:charts)if(value instanceof Map<?,?> chart)blocks.add(chartBlock(++chartIndex,chart,output));}
                else if(output.get("spec") instanceof Map<?,?>)blocks.add(chartBlock(++chartIndex,output,output));
            }
            Object artifacts=output.get("artifacts");if(artifacts instanceof Collection<?> values)for(Object value:values)if(value instanceof Map<?,?> artifact){Map<String,Object> block=artifactBlock(++artifactIndex,artifact);if(block!=null)blocks.add(block);}
        }
        Map<String,Object> responseMetadata=new LinkedHashMap<>(metadata);responseMetadata.put("chartCount",chartIndex);responseMetadata.put("artifactCount",chartIndex+artifactIndex);responseMetadata.put("source","LLM_AND_MCP");
        return Map.of("schemaVersion","1.1","responseId",UUID.randomUUID().toString(),"role","assistant","blocks",List.copyOf(blocks),"metadata",Map.copyOf(responseMetadata));
    }
    private Map<String,Object> chartBlock(int index,Map<?,?> chart,Map<?,?> toolOutput){
        Map<String,Object> content=new LinkedHashMap<>();content.put("format","VEGA_LITE");content.put("chartType",Objects.toString(chart.get("chartType"),"chart"));content.put("title",Objects.toString(chart.get("title"),"Chart "+index));content.put("spec",chart.get("spec"));content.put("downloadFormats",toolOutput.containsKey("downloadFormats")?toolOutput.get("downloadFormats"):List.of("PNG","SVG","CSV","VEGA_LITE_JSON"));
        return Map.of("id","chart-"+index,"type","chart","content",Map.copyOf(content));
    }
    private Map<String,Object> artifactBlock(int index,Map<?,?> artifact){String type=Objects.toString(artifact.get("type"),"").toLowerCase(Locale.ROOT);if(!Set.of("image","table","file","code","notice").contains(type))return null;Object raw=artifact.get("content");if(raw==null)return null;return Map.of("id","artifact-"+index,"type",type,"content",raw);}
}
