package dev.agentstudio.runtime;

import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.api.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConfigRuntimeAdapter implements AgentRuntimeAdapter {
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
        var tools=pipeline.execute(agent,request.input(),context.subjectId(),plan.capabilities(),plan.strategy());
        if(!agent.toolCapabilitiesRequired().isEmpty()&&tools.steps().isEmpty()){
            return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,Map.of("groundingPolicy","MCP_ONLY","toolResults",List.of()),List.of(),List.of(new AgentExecutionResult.SemanticEvent("grounding.failed",Instant.now(),Map.of("policy","MCP_ONLY","reason","no MCP evidence was produced"))),new AgentExecutionResult.Usage(0,0,0,0),"MCP-only grounding requires at least one successful tool result");
        }
        Map<String,Object> enriched=new java.util.LinkedHashMap<>(request.input());enriched.put("toolResults",tools.steps());enriched.put("instruction","Use the ordered tool results as grounded evidence. Do not request a repeated tool call. Write the concise narrative portion of a standard agent response; MCP chart artifacts will be attached to that same response document by the platform.");enriched.put("selectedCapabilities",plan.capabilities());
        var generated=models.invoke(context.tenantId(),agent.modelProfile(),Map.copyOf(enriched));
        if(generated.isPresent()){
            var result=generated.get();Map<String,Object> output=Map.of("answer",result.text(),"response",responses.compose(result.text(),tools.steps(),Map.of("agentId",agent.agentId(),"model",result.model(),"provider",result.provider())),"model",result.model(),"provider",result.provider(),"toolResults",tools.steps(),"groundingPolicy",agent.toolCapabilitiesRequired().isEmpty()?"MODEL":"MCP_ONLY","analysis",tools.analysis());
            long plannerInput=plan.modelUsage()==null?0:plan.modelUsage().inputTokens(),plannerOutput=plan.modelUsage()==null?0:plan.modelUsage().outputTokens(),plannerCost=plan.modelUsage()==null?0:plan.modelUsage().costMicros();int calls=plan.modelUsage()==null?1:2;
            List<AgentExecutionResult.SemanticEvent> events=new java.util.ArrayList<>();events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.started",Instant.now(),Map.of("maxIterations",tools.analysis().maxIterations(),"llmPolicy","ON_DEMAND","cacheStrategy",tools.analysis().cacheStrategy())));events.add(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"attachedCapabilityCount",agent.toolCapabilitiesRequired().size(),"modelCall",plan.modelUsage()!=null)));tools.steps().forEach(step->events.add(new AgentExecutionResult.SemanticEvent("tool.completed",Instant.now(),Map.of("iteration",step.iteration(),"capability",step.capability(),"providerId",step.providerId(),"transport",step.transport(),"cacheHit",step.cacheHit()))));events.add(new AgentExecutionResult.SemanticEvent("model.completed",Instant.now(),Map.of("profile",agent.modelProfile(),"model",result.model(),"provider",result.provider(),"callPolicy","ON_DEMAND","inputTokens",result.inputTokens(),"outputTokens",result.outputTokens(),"costMicros",result.costMicros())));events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.completed",Instant.now(),Map.of("iterations",tools.analysis().iterations(),"modelCalls",calls,"cacheHits",tools.analysis().cacheHits())));
            return new AgentExecutionResult(AgentExecutionResult.Status.COMPLETED,output,List.of(),List.copyOf(events),new AgentExecutionResult.Usage(Math.toIntExact(result.inputTokens()+plannerInput),Math.toIntExact(result.outputTokens()+plannerOutput),calls,result.costMicros()+plannerCost),null);
        }
        String narrative="Completed the bounded analysis loop without an LLM call";Map<String,Object> output=Map.of("agentId",agent.agentId(),"version",agent.version(),"result",tools.context(),"toolResults",tools.steps(),"analysis",tools.analysis(),"message",narrative,"response",responses.compose(narrative,tools.steps(),Map.of("agentId",agent.agentId())));
        List<AgentExecutionResult.SemanticEvent> events=new java.util.ArrayList<>();events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.started",Instant.now(),Map.of("maxIterations",tools.analysis().maxIterations(),"llmPolicy","ON_DEMAND","cacheStrategy",tools.analysis().cacheStrategy())));events.add(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"modelCall",plan.modelUsage()!=null)));tools.steps().forEach(step->events.add(new AgentExecutionResult.SemanticEvent("tool.completed",Instant.now(),Map.of("iteration",step.iteration(),"capability",step.capability(),"providerId",step.providerId(),"transport",step.transport(),"cacheHit",step.cacheHit()))));events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.completed",Instant.now(),Map.of("iterations",tools.analysis().iterations(),"modelCalls",plan.modelUsage()==null?0:1,"cacheHits",tools.analysis().cacheHits(),"reason","no valid provider model configured for synthesis")));
        long plannerInput=plan.modelUsage()==null?0:plan.modelUsage().inputTokens(),plannerOutput=plan.modelUsage()==null?0:plan.modelUsage().outputTokens(),plannerCost=plan.modelUsage()==null?0:plan.modelUsage().costMicros();return new AgentExecutionResult(AgentExecutionResult.Status.COMPLETED,output,List.of(),List.copyOf(events),new AgentExecutionResult.Usage(Math.toIntExact(plannerInput),Math.toIntExact(plannerOutput),plan.modelUsage()==null?0:1,plannerCost),null);
    }
}
