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
        List<String> order=order(agent);List<Step> steps=new ArrayList<>();Map<String,Object> shared=new LinkedHashMap<>(input);Set<String> completed=new LinkedHashSet<>();List<String> skipped=new ArrayList<>();int maxIterations=Math.min(12,Math.max(1,agent.toolCapabilitiesRequired().size()));
        int iteration=0;int cacheHits=0;for(String capability:order){if(iteration>=maxIterations)break;if(!agent.toolCapabilitiesRequired().contains(capability)||!completed.add(capability)){skipped.add(capability);continue;}iteration++;Map<String,Object> invocationInput=Map.copyOf(shared);Optional<Object> cached=cacheable(capability)?cache.get(agent.tenantId(),capability,invocationInput):Optional.empty();Object output;boolean cacheHit=cached.isPresent();String providerId="cache",transport="CACHE";if(cacheHit){output=cached.get();cacheHits++;}else{var invocation=providers.invoke(agent.tenantId(),agent.agentId(),agent.version(),subjectId,capability,shared);output=invocation.output();providerId=invocation.providerId();transport=invocation.transport();if(cacheable(capability))cache.put(agent.tenantId(),capability,invocationInput,output);}steps.add(new Step(iteration,capability,providerId,transport,output,cacheHit));shared.put("previousResult",output);shared.put(capability,output);}
        return new Result(List.copyOf(steps),Map.copyOf(shared),new Analysis(iteration,maxIterations,List.copyOf(skipped),true,"EXACT_REDIS_SHA256",cacheHits,"ON_DEMAND",2));
    }
    private boolean cacheable(String capability){return capability.endsWith(".describe-schema")||capability.endsWith(".query-readonly")||"web.fetch".equals(capability)||"web.extract".equals(capability)||capability.startsWith("redis.");}
    private List<String> order(AgentVersion agent){
        if(agent.promptRef()!=null&&agent.promptRef().startsWith("plan://"))try{JsonNode plan=json.readTree(URLDecoder.decode(agent.promptRef().substring(7),StandardCharsets.UTF_8));List<String> values=new ArrayList<>();plan.path("order").forEach(n->values.add(n.asText()));return values;}catch(Exception ignored){}
        return new ArrayList<>(agent.toolCapabilitiesRequired());
    }
    record Step(int iteration,String capability,String providerId,String transport,Object output,boolean cacheHit){}
    record Analysis(int iterations,int maxIterations,List<String> skippedDuplicateOrUnbound,boolean reusedToolResults,String cacheStrategy,int cacheHits,String llmPolicy,int maxModelCalls){}
    record Result(List<Step> steps,Map<String,Object> context,Analysis analysis){}
}
