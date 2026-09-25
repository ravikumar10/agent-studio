package dev.agentstudio.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.HostConfig;
import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.api.AgentExecutionRequest;
import dev.agentstudio.runtime.api.AgentExecutionResult;
import dev.agentstudio.runtime.api.ExecutionContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class IsolatedWorkerClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final DockerClient docker;
    private final String image;
    private final String network;
    private final String token;
    private final Map<String,String> active = new ConcurrentHashMap<>();

    IsolatedWorkerClient(ObjectMapper json, DockerClient docker,
                         @Value("${runtime.docker-worker-image:compose-docker-agent-worker:latest}") String image,
                         @Value("${runtime.docker-network:compose_default}") String network,
                         @Value("${runtime.worker-token:local-worker-token-change-me}") String token) {
        this.json=json; this.docker=docker; this.image=image; this.network=network; this.token=token;
    }

    AgentExecutionResult execute(AgentVersion agent, AgentExecutionRequest request, ExecutionContext context) {
        String name="agent-run-"+context.runId().replace("-","");
        String containerId=null;
        try {
            List<String> env=new ArrayList<>(List.of(
                    "SERVER_PORT=8080","DATABASE_URL="+environment("DATABASE_URL","jdbc:postgresql://postgres:5432/agent_studio"),
                    "DATABASE_USER="+environment("DATABASE_USER","agent_studio"),"DATABASE_PASSWORD="+environment("DATABASE_PASSWORD","agent_studio"),
                    "REDIS_HOST="+environment("REDIS_HOST","redis"),"REDIS_PORT="+environment("REDIS_PORT","6379"),
                    "RUNTIME_WORKER_TOKEN="+token,"AGENT_STUDIO_ENCRYPTION_KEY="+environment("AGENT_STUDIO_ENCRYPTION_KEY","local-development-encryption-key-change-me"),
                    "RUNTIME_TOOLS_WEB_URL="+environment("RUNTIME_TOOLS_WEB_URL","http://web-reader-tool:8080"),
                    "RUNTIME_TOOLS_DATABASE_URL="+environment("RUNTIME_TOOLS_DATABASE_URL","http://database-reader-tool:8080")));
            copyIfPresent(env,"OPENAI_API_KEY"); copyIfPresent(env,"ANTHROPIC_API_KEY");
            containerId=createContainer(name,env,context.runId());
            active.put(context.runId(),containerId); docker.startContainerCmd(containerId).exec();
            String body=json.writeValueAsString(new WorkerInvocation(agent,request,context));
            Duration timeout=context.deadline()==null?Duration.ofMinutes(5):Duration.ofSeconds(Math.max(1,Duration.between(java.time.Instant.now(),context.deadline()).toSeconds()));
            HttpRequest invocation=HttpRequest.newBuilder(URI.create("http://"+name+":8080/internal/v1/runtime-executions"))
                    .timeout(timeout).header("Content-Type","application/json").header("X-Internal-Worker-Token",token)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=sendWhenReady(invocation,context.runId());
            if(response.statusCode()/100!=2)throw new IllegalStateException("ephemeral Docker worker returned "+response.statusCode()+": "+response.body());
            return json.readValue(response.body(),AgentExecutionResult.class);
        } catch(Exception error) {
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,null,null,null,null,"Docker worker dispatch failed: "+error.getMessage());
        } finally { cleanup(context.runId(),containerId); }
    }

    void terminate(String runId){cleanup(runId,active.get(runId));}
    private String createContainer(String name,List<String> env,String runId)throws InterruptedException{
        RuntimeException last=null;
        for(int attempt=0;attempt<3;attempt++)try{
            return docker.createContainerCmd(image).withName(name).withEnv(env)
                    .withLabels(Map.of("agentstudio.run-id",runId,"agentstudio.ephemeral","true"))
                    .withHostConfig(HostConfig.newHostConfig().withNetworkMode(network).withAutoRemove(false)).exec().getId();
        }catch(RuntimeException error){last=error;Thread.sleep(150L*(attempt+1));}
        throw last;
    }
    private HttpResponse<String> sendWhenReady(HttpRequest request,String runId)throws Exception{
        Exception last=null;
        for(int attempt=0;attempt<40;attempt++)try{return http.send(request,HttpResponse.BodyHandlers.ofString());}catch(java.net.ConnectException error){last=error;if(!active.containsKey(runId))throw new InterruptedException("run was cancelled");Thread.sleep(250);}
        throw new IllegalStateException("ephemeral worker did not become ready",last);
    }
    private void cleanup(String runId,String id){active.remove(runId);if(id==null)return;try{docker.stopContainerCmd(id).withTimeout(3).exec();}catch(Exception ignored){}try{docker.removeContainerCmd(id).withForce(true).withRemoveVolumes(true).exec();}catch(Exception ignored){}}
    private static String environment(String key,String fallback){String value=System.getenv(key);return value==null||value.isBlank()?fallback:value;}
    private static void copyIfPresent(List<String> env,String key){String value=System.getenv(key);if(value!=null&&!value.isBlank())env.add(key+"="+value);}
    record WorkerInvocation(AgentVersion agent,AgentExecutionRequest request,ExecutionContext context){}
}
