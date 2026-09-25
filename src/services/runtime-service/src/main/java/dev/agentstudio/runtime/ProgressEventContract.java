package dev.agentstudio.runtime;

import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Stable semantic progress metadata; never contains prompts, chain-of-thought, credentials, or raw provider payloads. */
@Component
class ProgressEventContract {
    Map<String,Object> decorate(String type,Map<String,Object> attributes){Progress progress=progress(type,attributes);if(progress==null)return attributes;var result=new java.util.LinkedHashMap<>(attributes);result.put("progress",Map.of("schemaVersion","1.0","kind","agent.progress","stage",progress.stage(),"status",progress.status(),"title",progress.title(),"summary",progress.summary()));return Map.copyOf(result);}
    private Progress progress(String type,Map<String,Object> values){return switch(type){
        case "run.started"->new Progress("EXECUTION","STARTED","Agent started","Preparing the requested task.");
        case "mcp.pipeline.started"->new Progress("TOOLS","STARTED","Gathering information","Running the capabilities needed for this response.");
        case "agent.analysis.started"->new Progress("PLANNING","STARTED","Understanding the request","Selecting the shortest grounded path to an answer.");
        case "agent.plan.completed"->new Progress("PLANNING","COMPLETED","Plan ready","The required tools and response path were selected.");
        case "tool.completed"->new Progress("TOOLS","COMPLETED",friendly(values.get("capability"))+" completed","Grounded evidence is ready for the response.");
        case "model.completed"->new Progress("SYNTHESIS","COMPLETED","Answer composed","The model formatted the grounded evidence into the final response.");
        case "agent.analysis.completed"->new Progress("SYNTHESIS","COMPLETED","Response ready","Analysis and response assembly completed.");
        case "run.failed"->new Progress("EXECUTION","FAILED","Run failed","The task could not be completed.");
        case "run.cancelled"->new Progress("EXECUTION","CANCELLED","Run stopped","Execution was stopped and its worker was released.");
        default->null;
    };}
    private String friendly(Object capability){String value=Objects.toString(capability,"Tool").replace('.',' ').replace('-',' ');return Character.toUpperCase(value.charAt(0))+value.substring(1);}
    private record Progress(String stage,String status,String title,String summary){}
}
