package dev.agentstudio.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.agentstudio.domain.AgentVersion;
import dev.agentstudio.runtime.api.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class RemoteRuntimeAdapter implements AgentRuntimeAdapter {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); private final ObjectMapper json;
    public RemoteRuntimeAdapter(ObjectMapper json){this.json=json;}
    public boolean supports(AgentVersion.RuntimeType t){return t==AgentVersion.RuntimeType.REMOTE_HTTP||t==AgentVersion.RuntimeType.REMOTE_A2A;}
    public AgentExecutionResult execute(AgentVersion agent,AgentExecutionRequest request,ExecutionContext context){
        try {
            var body=json.writeValueAsString(new Invocation(agent.agentId(),agent.version(),request,context));
            var http=HttpRequest.newBuilder(URI.create(agent.remoteEndpointRef())).timeout(Duration.ofSeconds(Math.max(1,Duration.between(java.time.Instant.now(),context.deadline()).toSeconds()))).header("Content-Type","application/json").header("X-Tenant-Id",context.tenantId()).header("X-Run-Id",context.runId()).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(http,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()/100!=2){String detail=response.body()==null?"":response.body().replaceAll("\\s+"," ").trim();if(detail.length()>500)detail=detail.substring(0,500);throw new IllegalStateException("remote runtime returned "+response.statusCode()+(detail.isBlank()?"":": "+detail));}
            return json.readValue(response.body(),AgentExecutionResult.class);
        }catch(Exception e){return new AgentExecutionResult(AgentExecutionResult.Status.FAILED,null,null,null,null,e.getMessage());}
    }
    private record Invocation(String agentId,String agentVersion,AgentExecutionRequest request,ExecutionContext context){}
}
