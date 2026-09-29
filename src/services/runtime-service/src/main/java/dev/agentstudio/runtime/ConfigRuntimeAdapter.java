package dev.agentstudio.runtime;

import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.api.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ConfigRuntimeAdapter implements AgentRuntimeAdapter {
    private static final Set<String> POST_RESPONSE_DELIVERY = Set.of("slack.messages.send");
    private final ModelGateway models; private final AgentCreatorService creators; private final CapabilityPipeline pipeline; private final AgentLoopPlanner planner; private final StandardAgentResponseComposer responses; private final ObjectMapper json;
    public ConfigRuntimeAdapter(ModelGateway models,AgentCreatorService creators,CapabilityPipeline pipeline,AgentLoopPlanner planner,StandardAgentResponseComposer responses,ObjectMapper json){this.models=models;this.creators=creators;this.pipeline=pipeline;this.planner=planner;this.responses=responses;this.json=json;}
    public boolean supports(AgentVersion.RuntimeType type) { return type == AgentVersion.RuntimeType.CONFIG; }
    public AgentExecutionResult execute(AgentVersion agent, AgentExecutionRequest request, ExecutionContext context) {
        if (Instant.now().isAfter(context.deadline())) return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,Map.of(),List.of(),List.of(),null,"deadline exceeded");
        if(agent.toolCapabilitiesRequired().contains("platform.agents.create")){
            var created=creators.create(context.tenantId(),request.input());
            Map<String,Object> output=new java.util.LinkedHashMap<>();output.put("answer",created.answer());output.put("response",responses.compose(created.answer(),List.of(),Map.of("agentId",agent.agentId())));output.put("createdAgentId",created.id());output.put("displayName",created.displayName());output.put("modelProfile",created.modelProfile());output.put("toolCapabilities",created.toolCapabilities());output.put("skills",created.skills());
            return new AgentExecutionResult(AgentExecutionResult.Status.COMPLETED,Map.copyOf(output),List.of(),List.of(new AgentExecutionResult.SemanticEvent("agent.created",Instant.now(),Map.of("agentId",created.id(),"modelProfile",created.modelProfile()))),new AgentExecutionResult.Usage(0,0,0,1),null);
        }
        var plan=planner.plan(context.tenantId(),agent.modelProfile(),agent.promptRef(),request.input(),agent.toolCapabilitiesRequired());
        var deliveryCapabilities=plan.capabilities().stream().filter(POST_RESPONSE_DELIVERY::contains).toList();
        var workCapabilities=plan.capabilities().stream().filter(capability->!POST_RESPONSE_DELIVERY.contains(capability)).toList();
        CapabilityPipeline.Result tools;
        try{tools=pipeline.execute(agent,request.input(),context.subjectId(),workCapabilities,plan.strategy(),plan.arguments());}
        catch(CapabilityPipeline.ToolCallFailure failure){List<AgentExecutionResult.SemanticEvent> events=List.of(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"parameterizedCapabilities",plan.arguments().keySet(),"skillGuided",plan.strategy().contains("SKILL_GUIDED"),"modelCall",plan.modelUsage()!=null)),new AgentExecutionResult.SemanticEvent("tool.failed",Instant.now(),Map.of("iteration",failure.iteration(),"capability",failure.capability(),"request",failure.request(),"error",failure.getMessage())));long input=plan.modelUsage()==null?0:plan.modelUsage().inputTokens(),output=plan.modelUsage()==null?0:plan.modelUsage().outputTokens(),cost=plan.modelUsage()==null?0:plan.modelUsage().costMicros();return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,Map.of("analysis",Map.of("planningStrategy",plan.strategy(),"selectedCapabilities",plan.capabilities()),"toolResults",List.of()),List.of(),events,new AgentExecutionResult.Usage(Math.toIntExact(input),Math.toIntExact(output),plan.modelUsage()==null?0:1,cost),failure.getMessage());}
        Map<String,Object> enriched=new java.util.LinkedHashMap<>(request.input());enriched.put("toolResults",tools.steps());enriched.put("instruction",initialPrompt(agent)+"\nUse the ordered tool results as grounded evidence. Do not request a repeated tool call. Write the concise narrative portion of a standard agent response; MCP chart artifacts will be attached to that same response document by the platform.");enriched.put("selectedCapabilities",plan.capabilities());
        var generated=models.invoke(context.tenantId(),agent.modelProfile(),Map.copyOf(enriched));
        if(generated.isPresent()){
            var result=generated.get();
            String report=withoutUnverifiedDeliveryStatus(result.text());
            CapabilityPipeline.Result delivery;
            try{delivery=deliver(agent,request,context,deliveryCapabilities,plan,report,tools);}
            catch(CapabilityPipeline.ToolCallFailure failure){
                List<AgentExecutionResult.SemanticEvent> events=new ArrayList<>();
                events.add(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"parameterizedCapabilities",plan.arguments().keySet(),"skillGuided",plan.strategy().contains("SKILL_GUIDED"),"modelCall",plan.modelUsage()!=null)));
                tools.steps().forEach(step->events.add(new AgentExecutionResult.SemanticEvent("tool.completed",Instant.now(),toolEvent(step))));
                events.add(new AgentExecutionResult.SemanticEvent("model.completed",Instant.now(),Map.of("profile",agent.modelProfile(),"model",result.model(),"provider",result.provider(),"callPolicy","ON_DEMAND","inputTokens",result.inputTokens(),"outputTokens",result.outputTokens(),"costMicros",result.costMicros())));
                events.add(new AgentExecutionResult.SemanticEvent("tool.failed",Instant.now(),Map.of("iteration",failure.iteration()+tools.steps().size(),"capability",failure.capability(),"request",failure.request(),"error",failure.getMessage())));
                long plannerInput=plan.modelUsage()==null?0:plan.modelUsage().inputTokens(),plannerOutput=plan.modelUsage()==null?0:plan.modelUsage().outputTokens(),plannerCost=plan.modelUsage()==null?0:plan.modelUsage().costMicros();int calls=plan.modelUsage()==null?1:2;
                Map<String,Object> failureOutput=new LinkedHashMap<>();failureOutput.put("answer",report);failureOutput.put("toolResults",tools.steps());failureOutput.put("analysis",Map.of("planningStrategy",plan.strategy(),"selectedCapabilities",plan.capabilities()));
                return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,Map.copyOf(failureOutput),List.of(),List.copyOf(events),new AgentExecutionResult.Usage(Math.toIntExact(result.inputTokens()+plannerInput),Math.toIntExact(result.outputTokens()+plannerOutput),calls,result.costMicros()+plannerCost),failure.getMessage());
            }
            List<CapabilityPipeline.Step> allSteps=concat(tools.steps(),delivery.steps());
            var analysis=merge(tools,delivery,plan);
            String answer=report+deliveryConfirmation(delivery);
            Map<String,Object> output=Map.of("answer",answer,"response",responses.compose(answer,allSteps,Map.of("agentId",agent.agentId(),"model",result.model(),"provider",result.provider())),"model",result.model(),"provider",result.provider(),"toolResults",allSteps,"groundingPolicy",allSteps.isEmpty()?"MODEL":"MCP_ONLY","analysis",analysis);
            long plannerInput=plan.modelUsage()==null?0:plan.modelUsage().inputTokens(),plannerOutput=plan.modelUsage()==null?0:plan.modelUsage().outputTokens(),plannerCost=plan.modelUsage()==null?0:plan.modelUsage().costMicros();int calls=plan.modelUsage()==null?1:2;
            List<AgentExecutionResult.SemanticEvent> events=new java.util.ArrayList<>();events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.started",Instant.now(),Map.of("maxIterations",analysis.maxIterations(),"llmPolicy","ON_DEMAND","cacheStrategy",analysis.cacheStrategy())));events.add(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"parameterizedCapabilities",plan.arguments().keySet(),"skillGuided",plan.strategy().contains("SKILL_GUIDED"),"attachedCapabilityCount",agent.toolCapabilitiesRequired().size(),"modelCall",plan.modelUsage()!=null)));allSteps.forEach(step->events.add(new AgentExecutionResult.SemanticEvent("tool.completed",Instant.now(),toolEvent(step))));events.add(new AgentExecutionResult.SemanticEvent("model.completed",Instant.now(),Map.of("profile",agent.modelProfile(),"model",result.model(),"provider",result.provider(),"callPolicy","ON_DEMAND","inputTokens",result.inputTokens(),"outputTokens",result.outputTokens(),"costMicros",result.costMicros())));events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.completed",Instant.now(),Map.of("iterations",analysis.iterations(),"modelCalls",calls,"cacheHits",analysis.cacheHits())));
            return new AgentExecutionResult(AgentExecutionResult.Status.COMPLETED,output,List.of(),List.copyOf(events),new AgentExecutionResult.Usage(Math.toIntExact(result.inputTokens()+plannerInput),Math.toIntExact(result.outputTokens()+plannerOutput),calls,result.costMicros()+plannerCost),null);
        }
        String narrative="Completed the bounded analysis loop without an LLM call";Map<String,Object> output=Map.of("agentId",agent.agentId(),"version",agent.version(),"result",tools.context(),"toolResults",tools.steps(),"analysis",tools.analysis(),"message",narrative,"response",responses.compose(narrative,tools.steps(),Map.of("agentId",agent.agentId())));
        List<AgentExecutionResult.SemanticEvent> events=new java.util.ArrayList<>();events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.started",Instant.now(),Map.of("maxIterations",tools.analysis().maxIterations(),"llmPolicy","ON_DEMAND","cacheStrategy",tools.analysis().cacheStrategy())));events.add(new AgentExecutionResult.SemanticEvent("agent.plan.completed",Instant.now(),Map.of("strategy",plan.strategy(),"reason",plan.reason(),"selectedCapabilities",plan.capabilities(),"parameterizedCapabilities",plan.arguments().keySet(),"skillGuided",plan.strategy().contains("SKILL_GUIDED"),"modelCall",plan.modelUsage()!=null)));tools.steps().forEach(step->events.add(new AgentExecutionResult.SemanticEvent("tool.completed",Instant.now(),toolEvent(step))));events.add(new AgentExecutionResult.SemanticEvent("agent.analysis.completed",Instant.now(),Map.of("iterations",tools.analysis().iterations(),"modelCalls",plan.modelUsage()==null?0:1,"cacheHits",tools.analysis().cacheHits(),"reason","no valid provider model configured for synthesis")));
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
        return pipeline.execute(agent,Map.copyOf(input),context.subjectId(),capabilities,plan.strategy(),plan.arguments());
    }

    private static List<CapabilityPipeline.Step> concat(List<CapabilityPipeline.Step> first,List<CapabilityPipeline.Step> second){
        List<CapabilityPipeline.Step> result=new ArrayList<>(first);int offset=first.size();
        for(var step:second)result.add(new CapabilityPipeline.Step(step.iteration()+offset,step.capability(),step.providerId(),step.transport(),step.request(),step.output(),step.cacheHit()));
        return List.copyOf(result);
    }

    private static CapabilityPipeline.Analysis merge(CapabilityPipeline.Result work,CapabilityPipeline.Result delivery,AgentLoopPlanner.Plan plan){
        return new CapabilityPipeline.Analysis(work.analysis().iterations()+delivery.analysis().iterations(),work.analysis().maxIterations()+delivery.analysis().maxIterations(),concatStrings(work.analysis().skippedDuplicateOrUnbound(),delivery.analysis().skippedDuplicateOrUnbound()),true,work.analysis().cacheStrategy(),work.analysis().cacheHits()+delivery.analysis().cacheHits(),"ON_DEMAND",2,plan.strategy(),work.analysis().attachedCapabilities(),plan.capabilities());
    }

    private static List<String> concatStrings(List<String> first,List<String> second){List<String> result=new ArrayList<>(first);result.addAll(second);return List.copyOf(result);}

    static String withoutUnverifiedDeliveryStatus(String text){
        if(text==null)return "";
        return text.replaceAll("(?ims)\\n+#{1,6}\\s+(?:Slack|Email|Teams)\\s+Delivery\\s*\\n.*?(?=\\n#{1,6}\\s+|\\z)","").stripTrailing();
    }

    private static String deliveryConfirmation(CapabilityPipeline.Result delivery){
        for(var step:delivery.steps())if("slack.messages.send".equals(step.capability())&&step.output() instanceof Map<?,?> value){
            boolean sent=Boolean.TRUE.equals(value.get("sent"));String channel=java.util.Objects.toString(value.get("channel"),"configured channel");
            if(sent)return "\n\n> **Delivered to Slack** · Channel `"+channel+"`.";
        }
        return "";
    }

    private static Map<String,Object> toolEvent(CapabilityPipeline.Step step){
        Map<String,Object> event=new LinkedHashMap<>();event.put("iteration",step.iteration());event.put("capability",step.capability());event.put("providerId",step.providerId());event.put("transport",step.transport());event.put("cacheHit",step.cacheHit());event.put("request",step.request());if(step.output()!=null)event.put("response",step.output());return Map.copyOf(event);
    }
    private String initialPrompt(AgentVersion agent){
        if(agent.promptRef()==null||!agent.promptRef().startsWith("plan://"))return "Follow the immutable agent definition and attached skills.";
        try{return json.readTree(URLDecoder.decode(agent.promptRef().substring(7),StandardCharsets.UTF_8)).path("initialPrompt").asText("Follow the immutable agent definition and attached skills.");}
        catch(Exception ignored){return "Follow the immutable agent definition and attached skills.";}
    }
}
