package dev.agentstudio.runtime;

import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.RunModels.*;
import dev.agentstudio.runtime.api.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

@Service
public class RunService {
    private final RunStore store; private final List<AgentRuntimeAdapter> adapters; private final RunEventStream stream; private final ExecutionPlacementService placements; private final IsolatedWorkerClient worker; private final ProgressEventContract progress; private final SessionContextService sessions; private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor(); private final ConcurrentMap<String,Future<?>> active=new ConcurrentHashMap<>();
    public RunService(RunStore store,List<AgentRuntimeAdapter> adapters,RunEventStream stream,ExecutionPlacementService placements,IsolatedWorkerClient worker,ProgressEventContract progress,SessionContextService sessions){this.store=store;this.adapters=adapters;this.stream=stream;this.placements=placements;this.worker=worker;this.progress=progress;this.sessions=sessions;}
    public RunView start(String tenant,StartRunRequest r){
        AgentVersion agent=store.resolve(tenant,r.agentId(),r.version()); String id=UUID.randomUUID().toString();
        String sessionId=r.sessionId()==null||r.sessionId().isBlank()?id:r.sessionId();String subject=Objects.requireNonNullElse(r.subjectId(),"anonymous");sessions.open(tenant,sessionId,agent.agentId(),agent.version(),subject);SessionContextService.Context sessionContext=sessions.load(tenant,sessionId);Map<String,Object> safeInput=RunTraceSanitizer.map(r.input());
        RunView run=new RunView(id,tenant,agent.agentId(),agent.version(),Status.CREATED,sessionId,safeInput,Instant.now(),null,null,Map.of(),null); store.create(run); publishRun(tenant,id); event(tenant,id,"run.created",Map.of("agentId",agent.agentId(),"version",agent.version(),"sessionId",sessionId));event(tenant,id,"agent.configuration.frozen",configurationSnapshot(agent));
        event(tenant,id,"session.context.loaded",Map.of("source",sessionContext.source(),"hotHit",sessionContext.hotHit(),"turnCount",sessionContext.conversation().size(),"evidenceCount",sessionContext.evidence().size()));sessions.userTurn(tenant,sessionId,id,r.input());Map<String,Object> contextualInput=sessions.enrich(r.input(),sessionContext);
        Runnable work=()->{try{execute(run,agent,r,contextualInput);}finally{active.remove(id);}}; if(r.async())active.put(id,executor.submit(work));else work.run(); return store.get(tenant,id);
    }
    private void execute(RunView run,AgentVersion agent,StartRunRequest r,Map<String,Object> contextualInput){try{store.running(run.tenantId(),run.runId());publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"run.started",Map.of());
        ExecutionContext ctx=new ExecutionContext(run.tenantId(),Objects.requireNonNullElse(r.subjectId(),"anonymous"),r.scopes(),run.runId(),null,r.deadline(),r.budget(),"INTERNAL",0);
        AgentExecutionRequest request=new AgentExecutionRequest(UUID.randomUUID().toString(),contextualInput,Map.of("sessionId",run.sessionId()));
        ExecutionPlacementService.Placement placement=placements.resolve(run.tenantId(),agent.agentId(),agent.version());
        if(placement==ExecutionPlacementService.Placement.AUTO)placement=agent.runtimeType()==AgentVersion.RuntimeType.CONFIG?ExecutionPlacementService.Placement.IN_PROCESS:ExecutionPlacementService.Placement.DOCKER;
        event(run.tenantId(),run.runId(),"execution.dispatched",Map.of("placement",placement.name()));
        if(!agent.toolCapabilitiesRequired().isEmpty())event(run.tenantId(),run.runId(),"mcp.pipeline.started",Map.of("capabilities",agent.toolCapabilitiesRequired(),"count",agent.toolCapabilitiesRequired().size()));
        AgentExecutionResult result;
        if(placement==ExecutionPlacementService.Placement.DOCKER){result=worker.execute(agent,request,ctx);}
        else if(placement==ExecutionPlacementService.Placement.KUBERNETES){throw new IllegalStateException("Kubernetes placement requires an applied deployment; generate and apply the workload from Deployments");}
        else {AgentRuntimeAdapter adapter=adapters.stream().filter(a->a.supports(agent.runtimeType())).findFirst().orElseThrow();result=adapter.execute(agent,request,ctx);}
        if(store.get(run.tenantId(),run.runId()).status()==Status.CANCELLED)return;
        result.events().forEach(e->event(run.tenantId(),run.runId(),e.type(),e.attributes()));
        if(!agent.toolCapabilitiesRequired().isEmpty())event(run.tenantId(),run.runId(),"mcp.pipeline.completed",Map.of("status",result.status().name()));
        event(run.tenantId(),run.runId(),"usage.recorded",Map.of("inputTokens",result.usage().inputTokens(),"outputTokens",result.usage().outputTokens(),"modelCalls",result.usage().modelCalls(),"costMicros",result.usage().costMicros()));
        if(result.status()==AgentExecutionResult.Status.COMPLETED){store.complete(run.tenantId(),run.runId(),result.output());sessions.assistantTurn(run.tenantId(),run.sessionId(),run.runId(),result.output());publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"session.turn.persisted",Map.of("role","ASSISTANT"));event(run.tenantId(),run.runId(),"run.completed",Map.of());}else{String error=Objects.requireNonNullElse(result.error(),result.status().name());store.fail(run.tenantId(),run.runId(),error);sessions.evidence(run.tenantId(),run.sessionId(),run.runId(),result.output());sessions.failure(run.tenantId(),run.sessionId(),run.runId(),error);publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"run.failed",Map.of("error",error));}
    }catch(Exception e){if(store.get(run.tenantId(),run.runId()).status()==Status.CANCELLED)return;String error=Objects.toString(e.getMessage(),"unknown");store.fail(run.tenantId(),run.runId(),error);sessions.failure(run.tenantId(),run.sessionId(),run.runId(),error);publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"run.failed",Map.of("error",error));}}
    public boolean cancel(String t,String id){boolean done=store.cancel(t,id);if(done){worker.terminate(id);Future<?> task=active.remove(id);if(task!=null)task.cancel(true);publishRun(t,id);event(t,id,"run.cancelled",Map.of("workloadTerminated",true));}return done;}
    private void publishRun(String tenant,String id){stream.runChanged(store.get(tenant,id));}
    private void event(String tenant,String id,String type,Map<String,Object> attributes){Map<String,Object> decorated=progress.decorate(type,attributes);store.event(tenant,id,type,decorated);stream.semanticEvent(tenant,id,type,decorated);}
    private Map<String,Object> configurationSnapshot(AgentVersion agent){Map<String,Object> value=new LinkedHashMap<>();value.put("agentId",agent.agentId());value.put("version",agent.version());value.put("checksum",agent.checksum());value.put("runtimeType",agent.runtimeType().name());value.put("hostingMode",agent.hostingMode().name());value.put("modelProfile",Objects.toString(agent.modelProfile(),""));value.put("promptRef",Objects.toString(agent.promptRef(),""));value.put("orderedToolCapabilities",List.copyOf(agent.toolCapabilitiesRequired()));value.put("agentCapabilities",List.copyOf(agent.agentCapabilitiesRequired()));value.put("executionPolicyRef",Objects.toString(agent.executionPolicyRef(),""));value.put("securityPolicyRef",Objects.toString(agent.securityPolicyRef(),""));return Map.copyOf(value);}
}
