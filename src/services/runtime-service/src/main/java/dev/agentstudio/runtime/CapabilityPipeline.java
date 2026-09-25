package dev.agentstudio.runtime;

import dev.agentstudio.domain.AgentVersion;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.*;

@Component
class CapabilityPipeline {
    private final ObjectMapper json; private final ToolResultCache cache; private final CapabilityProviderResolver providers;
    CapabilityPipeline(ObjectMapper json,ToolResultCache cache,CapabilityProviderResolver providers){this.json=json;this.cache=cache;this.providers=providers;}

    Result execute(AgentVersion agent,Map<String,Object> input,String subjectId){
        return execute(agent,input,subjectId,agent.toolCapabilitiesRequired(),"STATIC_ATTACHED");
    }
    Result empty(AgentVersion agent,String planningStrategy,Collection<String> selected){
        return new Result(List.of(),Map.of(),new Analysis(0,0,List.of(),true,"EXACT_REDIS_SHA256",0,"ON_DEMAND",2,planningStrategy,List.copyOf(agent.toolCapabilitiesRequired()),List.copyOf(selected)));
    }
    Result execute(AgentVersion agent,Map<String,Object> input,String subjectId,Collection<String> selected,String planningStrategy){
        Set<String> allowed=new LinkedHashSet<>(selected);List<String> order=order(agent).stream().filter(allowed::contains).toList();List<Step> steps=new ArrayList<>();Map<String,Object> shared=new LinkedHashMap<>(input);Set<String> completed=new LinkedHashSet<>();List<String> skipped=new ArrayList<>();int maxIterations=Math.min(12,Math.max(1,allowed.size()));
        int iteration=0;int cacheHits=0;for(String capability:order){if(iteration>=maxIterations)break;if(!agent.toolCapabilitiesRequired().contains(capability)||!completed.add(capability)){skipped.add(capability);continue;}iteration++;Map<String,Object> invocationInput=Map.copyOf(shared);Optional<Object> cached=cacheable(capability)?cache.get(agent.tenantId(),capability,invocationInput):Optional.empty();Object output;boolean cacheHit=cached.isPresent();String providerId="cache",transport="CACHE";if(cacheHit){output=cached.get();cacheHits++;}else{var invocation=providers.invoke(agent.tenantId(),agent.agentId(),agent.version(),subjectId,capability,shared);output=invocation.output();providerId=invocation.providerId();transport=invocation.transport();if(cacheable(capability))cache.put(agent.tenantId(),capability,invocationInput,output);}steps.add(new Step(iteration,capability,providerId,transport,output,cacheHit));shared.put("previousResult",output);shared.put(capability,output);}
        return new Result(List.copyOf(steps),Map.copyOf(shared),new Analysis(iteration,maxIterations,List.copyOf(skipped),true,"EXACT_REDIS_SHA256",cacheHits,"ON_DEMAND",2,planningStrategy,List.copyOf(agent.toolCapabilitiesRequired()),List.copyOf(allowed)));
    }
    private boolean cacheable(String capability){return !"knowledge.store".equals(capability)&&(capability.endsWith(".describe-schema")||capability.endsWith(".query-readonly")||"web.fetch".equals(capability)||"web.extract".equals(capability)||"http.request".equals(capability)||"browser.navigate".equals(capability)||"browser.extract".equals(capability)||"knowledge.search".equals(capability)||"chart.generate".equals(capability)||capability.startsWith("redis."));}
    private List<String> order(AgentVersion agent){
        List<String> values=new ArrayList<>();
        if(agent.promptRef()!=null&&agent.promptRef().startsWith("plan://"))try{JsonNode plan=json.readTree(URLDecoder.decode(agent.promptRef().substring(7),StandardCharsets.UTF_8));plan.path("order").forEach(n->values.add(n.asText()));}catch(Exception ignored){}
        values.removeIf(value->!agent.toolCapabilitiesRequired().contains(value));
        for(String capability:agent.toolCapabilitiesRequired())if(!values.contains(capability))values.add(capability);
        // Preserve user ordering within each phase while enforcing data dependencies.
        // Retrieval must happen before transforms/charts, and memory persistence is terminal.
        values.sort(Comparator.comparingInt(this::phase));
        return values;
    }
    private int phase(String capability){
        if("knowledge.search".equals(capability))return 0;
        if(capability.endsWith(".describe-schema"))return 10;
        if("slack.messages.read".equals(capability))return 20;
        if("web.fetch".equals(capability)||"http.request".equals(capability)||"browser.navigate".equals(capability)||capability.endsWith(".query-readonly")||capability.endsWith(".find-readonly")||capability.startsWith("redis."))return 20;
        if("browser.act".equals(capability))return 25;
        if("web.extract".equals(capability)||"browser.extract".equals(capability))return 30;
        if("chart.generate".equals(capability))return 90;
        if("email.draft".equals(capability))return 94;
        if("email.send".equals(capability))return 95;
        // Notifications are terminal side effects: send only after retrieval,
        // transformation, charts, and response artifacts have been produced.
        if("slack.messages.send".equals(capability))return 96;
        if("knowledge.store".equals(capability))return 100;
        return 50;
    }
    record Step(int iteration,String capability,String providerId,String transport,Object output,boolean cacheHit){}
    record Analysis(int iterations,int maxIterations,List<String> skippedDuplicateOrUnbound,boolean reusedToolResults,String cacheStrategy,int cacheHits,String llmPolicy,int maxModelCalls,String planningStrategy,List<String> attachedCapabilities,List<String> selectedCapabilities){}
    record Result(List<Step> steps,Map<String,Object> context,Analysis analysis){}
}
