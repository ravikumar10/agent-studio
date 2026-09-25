package dev.agentstudio.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.api.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Boundary adapter for an isolated Embabel worker. Embabel types never cross this contract. */
@Component
public class EmbabelRuntimeAdapter implements AgentRuntimeAdapter {
    private final String workerUrl; private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public EmbabelRuntimeAdapter(@Value("${runtime.embabel.worker-url:http://example-worker:8090/internal/v1/agent-invocations}")String workerUrl,ObjectMapper json){this.workerUrl=workerUrl;this.json=json;}
    public boolean supports(AgentVersion.RuntimeType type){return type==AgentVersion.RuntimeType.EMBABEL;}
    public AgentExecutionResult execute(AgentVersion agent,AgentExecutionRequest request,ExecutionContext context){
        try{String payload=json.writeValueAsString(new Invocation(agent.agentId(),agent.version(),agent.artifactRef(),request,context));
            HttpRequest http=HttpRequest.newBuilder(URI.create(workerUrl)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json").header("X-Tenant-Id",context.tenantId()).POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            HttpResponse<String> response=client.send(http,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()/100!=2)throw new IllegalStateException("Embabel worker returned "+response.statusCode());
            return json.readValue(response.body(),AgentExecutionResult.class);
        }catch(Exception e){return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,null,null,null,null,e.getMessage());}
    }
    private record Invocation(String agentId,String agentVersion,String artifactRef,AgentExecutionRequest request,ExecutionContext context){}
}
