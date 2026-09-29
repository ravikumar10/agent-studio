package dev.agentstudio.runtime;

import java.util.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Produces a bounded plan from attached logical capabilities; it never invents endpoints or tools. */
@Component
class AgentLoopPlanner {
    private final ModelGateway models; private final SkillContextResolver skills; private final ObjectMapper json;
    AgentLoopPlanner(ModelGateway models,SkillContextResolver skills,ObjectMapper json){this.models=models;this.skills=skills;this.json=json;}

    Plan plan(String tenant,String profile,String promptRef,Map<String,Object> input,Collection<String> attached){
        LinkedHashSet<String> available=configuredOrder(promptRef,attached);List<Map<String,String>> guidance=new ArrayList<>(skills.resolve(tenant,promptRef));String initialPrompt=initialPrompt(promptRef);if(!initialPrompt.isBlank())guidance.add(Map.of("id","agent-initial-prompt","reference","immutable-agent-version","description","Fixed agent role and objective","content",initialPrompt));Optional<ModelGateway.PlanResult> generated=models.plan(tenant,profile,input,available,guidance);
        // Configuration bounds and orders the available tools. The intelligent loop may choose
        // a subset, but it cannot invent tools or change the configured relative order.
        LinkedHashSet<String> selected=new LinkedHashSet<>();Map<String,Map<String,Object>> arguments=new LinkedHashMap<>();String strategy="LLM_INTENT_NO_PLAN";String reason="No model-backed tool plan was available";ModelGateway.ModelResult usage=null;
        if(generated.isPresent()){var plan=generated.get();usage=plan.model();if(plan.valid()){Set<String> requested=new LinkedHashSet<>(plan.capabilities());available.stream().filter(requested::contains).forEach(selected::add);arguments.putAll(plan.arguments());strategy="LLM_INTENT_CONFIG_ORDERED";reason=plan.reason();}}
        LinkedHashSet<String> ordered=selected;
        arguments.keySet().retainAll(ordered);return new Plan(List.copyOf(ordered),Map.copyOf(arguments),strategy,reason,usage);
    }
    private LinkedHashSet<String> configuredOrder(String promptRef,Collection<String> attached){
        LinkedHashSet<String> result=new LinkedHashSet<>();Set<String> allowed=new LinkedHashSet<>(attached);
        if(promptRef!=null&&promptRef.startsWith("plan://"))try{var plan=json.readTree(URLDecoder.decode(promptRef.substring(7),StandardCharsets.UTF_8));plan.path("order").forEach(value->{if(allowed.contains(value.asText()))result.add(value.asText());});}catch(Exception ignored){}
        allowed.forEach(result::add);return result;
    }
    private String initialPrompt(String promptRef){if(promptRef==null||!promptRef.startsWith("plan://"))return "";try{return json.readTree(URLDecoder.decode(promptRef.substring(7),StandardCharsets.UTF_8)).path("initialPrompt").asText("");}catch(Exception ignored){return "";}}
    record Plan(List<String> capabilities,Map<String,Map<String,Object>> arguments,String strategy,String reason,ModelGateway.ModelResult modelUsage){}
}
