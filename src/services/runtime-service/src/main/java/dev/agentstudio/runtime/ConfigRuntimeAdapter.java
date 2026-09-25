package dev.agentstudio.runtime;

import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.api.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ConfigRuntimeAdapter implements AgentRuntimeAdapter {
    private static final Set<String> POST_RESPONSE_DELIVERY = Set.of("slack.messages.send");
    private final ModelGateway models; private final AgentCreatorService creators; private final CapabilityPipeline pipeline; private final AgentLoopPlanner planner; private final StandardAgentResponseComposer responses;
    public ConfigRuntimeAdapter(ModelGateway models,AgentCreatorService creators,CapabilityPipeline pipeline,AgentLoopPlanner planner,StandardAgentResponseComposer responses){this.models=models;this.creators=creators;this.pipeline=pipeline;this.planner=planner;this.responses=responses;}
    public boolean supports(AgentVersion.RuntimeType type) { return type == AgentVersion.RuntimeType.CONFIG; }
    public AgentExecutionResult execute(AgentVersion agent, AgentExecutionRequest request, ExecutionContext context) {
        if (Instant.now().isAfter(context.deadline())) return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,Map.of(),List.of(),List.of(),null,"deadline exceeded");
        if(agent.toolCapabilitiesRequired().contains("platform.agents.create")){
            var created=creators.create(context.tenantId(),request.input());
            Map<String,Object> output=new java.util.LinkedHashMap<>();output.put("answer",created.answer());output.put("response",responses.compose(created.answer(),List.of(),Map.of("agentId",agent.agentId())));output.put("createdAgentId",created.id());output.put("displayName",created.displayName());output.put("modelProfile",created.modelProfile());output.put("toolCapabilities",created.toolCapabilities());output.put("skills",created.skills());
            return new AgentExecutionResult(AgentExecutionResult.Status.COMPLETED,Map.copyOf(output),List.of(),List.of(new AgentExecutionResult.SemanticEvent("agent.created",Instant.now(),Map.of("agentId",created.id(),"modelProfile",created.modelProfile()))),new AgentExecutionResult.Usage(0,0,0,1),null);
        }
        var plan=planner.plan(context.tenantId(),agent.modelProfile(),request.input(),agent.toolCapabilitiesRequired());
        var deliveryCapabilities=plan.capabilities().stream().filter(POST_RESPONSE_DELIVERY::contains).toList();
        var workCapabilities=plan.capabilities().stream().filter(capability->!POST_RESPONSE_DELIVERY.contains(capability)).toList();
        var tools=pipeline.execute(agent,request.input(),context.subjectId(),workCapabilities,plan.strategy());
        if(!agent.toolCapabilitiesRequired().isEmpty()&&tools.steps().isEmpty()&&deliveryCapabilities.isEmpty()){
            return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,Map.of("groundingPolicy","MCP_ONLY","toolResults",List.of()),List.of(),List.of(new AgentExecutionResult.SemanticEvent("grounding.failed",Instant.now(),Map.of("policy","MCP_ONLY","reason","no MCP evidence was produced"))),new AgentExecutionResult.Usage(0,0,0,0),"MCP-only grounding requires at least one successful tool result");
        }
        Map<String,Object> enriched=new java.util.LinkedHashMap<>(request.input());enriched.put("toolResults",tools.steps());enriched.put("instruction","Use the ordered tool results as grounded evidence. Do not request a repeated tool call. Write the concise narrative portion of a standard agent response; MCP chart artifacts will be attached to that same response document by the platform.");enriched.put("selectedCapabilities",plan.capabilities());
        var generated=models.invoke(context.tenantId(),agent.modelProfile(),Map.copyOf(enriched));
        if(generated.isPresent()){
            var result=generated.get();
            var delivery=deliver(agent,request,context,deliveryCapabilities,plan,result.text(),tools);
            List<CapabilityPipeline.Step> allSteps=concat(tools.steps(),delivery.steps());
            var analysis=merge(tools,delivery,plan);
            Map<String,Object> output=Map.of("answer",result.text(),"response",responses.compose(result.text(),allSteps,Map.of("agentId",agent.agentId(),"model",result.model(),"provider",result.provider())),"model",result.model(),"provider",result.provider(),"toolResults",allSteps,"groundingPolicy",agent.toolCapabilitiesRequired().isEmpty()?"MODEL":"MCP_ONLY","analysis",analysis);
            long plannerInput=plan.modelUsage()==null?0:plan.modelUsage().inputTokens(),plannerOutput=plan.modelUsage()==null?0:plan.modelUsage().outputTokens(),plannerCost=plan.modelUsage()==null?0:plan.modelUsage().costMicros();int calls=plan.modelUsage()==null?1:2;
            List<AgentExecutionResult.SemanticEvent> events=new java.util.ArrayList<>();events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.started",Instant.now(),Map.of("maxIterations",analysis.maxIterations(),"llmPolicy","ON_DEMAND","cacheStrategy",analysis.cacheStrategy())));events.add(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"attachedCapabilityCount",agent.toolCapabilitiesRequired().size(),"modelCall",plan.modelUsage()!=null)));allSteps.forEach(step->events.add(new AgentExecutionResult.SemanticEvent("tool.completed",Instant.now(),Map.of("iteration",step.iteration(),"capability",step.capability(),"providerId",step.providerId(),"transport",step.transport(),"cacheHit",step.cacheHit()))));events.add(new AgentExecutionResult.SemanticEvent("model.completed",Instant.now(),Map.of("profile",agent.modelProfile(),"model",result.model(),"provider",result.provider(),"callPolicy","ON_DEMAND","inputTokens",result.inputTokens(),"outputTokens",result.outputTokens(),"costMicros",result.costMicros())));events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.completed",Instant.now(),Map.of("iterations",analysis.iterations(),"modelCalls",calls,"cacheHits",analysis.cacheHits())));
            return new AgentExecutionResult(AgentExecutionResult.Status.COMPLETED,output,List.of(),List.copyOf(events),new AgentExecutionResult.Usage(Math.toIntExact(result.inputTokens()+plannerInput),Math.toIntExact(result.outputTokens()+plannerOutput),calls,result.costMicros()+plannerCost),null);
        }
        String narrative="Completed the bounded analysis loop without an LLM call";Map<String,Object> output=Map.of("agentId",agent.agentId(),"version",agent.version(),"result",tools.context(),"toolResults",tools.steps(),"analysis",tools.analysis(),"message",narrative,"response",responses.compose(narrative,tools.steps(),Map.of("agentId",agent.agentId())));
        List<AgentExecutionResult.SemanticEvent> events=new java.util.ArrayList<>();events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.started",Instant.now(),Map.of("maxIterations",tools.analysis().maxIterations(),"llmPolicy","ON_DEMAND","cacheStrategy",tools.analysis().cacheStrategy())));events.add(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"modelCall",plan.modelUsage()!=null)));tools.steps().forEach(step->events.add(new AgentExecutionResult.SemanticEvent("tool.completed",Instant.now(),Map.of("iteration",step.iteration(),"capability",step.capability(),"providerId",step.providerId(),"transport",step.transport(),"cacheHit",step.cacheHit()))));events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.completed",Instant.now(),Map.of("iterations",tools.analysis().iterations(),"modelCalls",plan.modelUsage()==null?0:1,"cacheHits",tools.analysis().cacheHits(),"reason","no valid provider model configured for synthesis")));
        long plannerInput=plan.modelUsage()==null?0:plan.modelUsage().inputTokens(),plannerOutput=plan.modelUsage()==null?0:plan.modelUsage().outputTokens(),plannerCost=plan.modelUsage()==null?0:plan.modelUsage().costMicros();return new AgentExecutionResult(AgentExecutionResult.Status.COMPLETED,output,List.of(),List.copyOf(events),new AgentExecutionResult.Usage(Math.toIntExact(plannerInput),Math.toIntExact(plannerOutput),plan.modelUsage()==null?0:1,plannerCost),null);
    }

    private CapabilityPipeline.Result deliver(AgentVersion agent,AgentExecutionRequest request,ExecutionContext context,List<String> capabilities,AgentLoopPlanner.Plan plan,String report,CapabilityPipeline.Result tools){
        if(capabilities.isEmpty())return pipeline.empty(agent,plan.strategy(),capabilities);
        Map<String,Object> input=new LinkedHashMap<>(request.input());
        input.putAll(tools.context());
        input.put("report",report);
        input.put("text",report);
        input.put("message",report);
        input.put("deliveryContentSource","FINAL_AGENT_RESPONSE");
        return pipeline.execute(agent,Map.copyOf(input),context.subjectId(),capabilities,plan.strategy());
    }

    private static List<CapabilityPipeline.Step> concat(List<CapabilityPipeline.Step> first,List<CapabilityPipeline.Step> second){
        List<CapabilityPipeline.Step> result=new ArrayList<>(first);int offset=first.size();
        for(var step:second)result.add(new CapabilityPipeline.Step(step.iteration()+offset,step.capability(),step.providerId(),step.transport(),step.output(),step.cacheHit()));
        return List.copyOf(result);
    }

    private static CapabilityPipeline.Analysis merge(CapabilityPipeline.Result work,CapabilityPipeline.Result delivery,AgentLoopPlanner.Plan plan){
        return new CapabilityPipeline.Analysis(work.analysis().iterations()+delivery.analysis().iterations(),work.analysis().maxIterations()+delivery.analysis().maxIterations(),concatStrings(work.analysis().skippedDuplicateOrUnbound(),delivery.analysis().skippedDuplicateOrUnbound()),true,work.analysis().cacheStrategy(),work.analysis().cacheHits()+delivery.analysis().cacheHits(),"ON_DEMAND",2,plan.strategy(),work.analysis().attachedCapabilities(),plan.capabilities());
    }

    private static List<String> concatStrings(List<String> first,List<String> second){List<String> result=new ArrayList<>(first);result.addAll(second);return List.copyOf(result);}
}
