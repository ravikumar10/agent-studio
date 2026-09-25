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
    private final RunStore store; private final List<AgentRuntimeAdapter> adapters; private final RunEventStream stream; private final ExecutionPlacementService placements; private final IsolatedWorkerClient worker; private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor(); private final ConcurrentMap<String,Future<?>> active=new ConcurrentHashMap<>();
    public RunService(RunStore store,List<AgentRuntimeAdapter> adapters,RunEventStream stream,ExecutionPlacementService placements,IsolatedWorkerClient worker){this.store=store;this.adapters=adapters;this.stream=stream;this.placements=placements;this.worker=worker;}
    public RunView start(String tenant,StartRunRequest r){
        AgentVersion agent=store.resolve(tenant,r.agentId(),r.version()); String id=UUID.randomUUID().toString();
        RunView run=new RunView(id,tenant,agent.agentId(),agent.version(),Status.CREATED,Instant.now(),null,null,Map.of(),null); store.create(run); publishRun(tenant,id); event(tenant,id,"run.created",Map.of("agentId",agent.agentId(),"version",agent.version()));
        Runnable work=()->{try{execute(run,agent,r);}finally{active.remove(id);}}; if(r.async())active.put(id,executor.submit(work));else work.run(); return store.get(tenant,id);
    }
    private void execute(RunView run,AgentVersion agent,StartRunRequest r){try{store.running(run.tenantId(),run.runId());publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"run.started",Map.of());
        ExecutionContext ctx=new ExecutionContext(run.tenantId(),Objects.requireNonNullElse(r.subjectId(),"anonymous"),r.scopes(),run.runId(),null,r.deadline(),r.budget(),"INTERNAL",0);
        AgentExecutionRequest request=new AgentExecutionRequest(UUID.randomUUID().toString(),r.input(),Map.of());
        ExecutionPlacementService.Placement placement=placements.resolve(run.tenantId(),agent.agentId(),agent.version());
        if(placement==ExecutionPlacementService.Placement.AUTO)placement=agent.runtimeType()==AgentVersion.RuntimeType.CONFIG?ExecutionPlacementService.Placement.IN_PROCESS:ExecutionPlacementService.Placement.DOCKER;
        event(run.tenantId(),run.runId(),"execution.dispatched",Map.of("placement",placement.name()));
        AgentExecutionResult result;
        if(placement==ExecutionPlacementService.Placement.DOCKER){result=worker.execute(agent,request,ctx);}
        else if(placement==ExecutionPlacementService.Placement.KUBERNETES){throw new IllegalStateException("Kubernetes placement requires an applied deployment; generate and apply the workload from Deployments");}
        else {AgentRuntimeAdapter adapter=adapters.stream().filter(a->a.supports(agent.runtimeType())).findFirst().orElseThrow();result=adapter.execute(agent,request,ctx);}
        if(store.get(run.tenantId(),run.runId()).status()==Status.CANCELLED)return;
        result.events().forEach(e->event(run.tenantId(),run.runId(),e.type(),e.attributes()));
        if(result.status()==AgentExecutionResult.Status.COMPLETED){store.complete(run.tenantId(),run.runId(),result.output());publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"run.completed",Map.of());}else{store.fail(run.tenantId(),run.runId(),Objects.requireNonNullElse(result.error(),result.status().name()));publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"run.failed",Map.of());}
    }catch(Exception e){if(store.get(run.tenantId(),run.runId()).status()==Status.CANCELLED)return;store.fail(run.tenantId(),run.runId(),e.getMessage());publishRun(run.tenantId(),run.runId());event(run.tenantId(),run.runId(),"run.failed",Map.of("error",Objects.toString(e.getMessage(),"unknown")));}}
    public boolean cancel(String t,String id){boolean done=store.cancel(t,id);if(done){worker.terminate(id);Future<?> task=active.remove(id);if(task!=null)task.cancel(true);publishRun(t,id);event(t,id,"run.cancelled",Map.of("workloadTerminated",true));}return done;}
    private void publishRun(String tenant,String id){stream.runChanged(store.get(tenant,id));}
    private void event(String tenant,String id,String type,Map<String,Object> attributes){store.event(tenant,id,type,attributes);stream.semanticEvent(tenant,id,type,attributes);}
}
