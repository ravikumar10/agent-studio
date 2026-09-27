package dev.agentstudio.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/v1/slack")
public class SlackEventController {
    private final JdbcClient jdbc;private final ObjectMapper json;private final SecretCipher cipher;private final RestClient runtime;private final RestClient slackMcp;private final ExecutorService tasks=Executors.newVirtualThreadPerTaskExecutor();
    SlackEventController(JdbcClient jdbc,ObjectMapper json,SecretCipher cipher,RestClient.Builder clients,
            @Value("${agent-studio.runtime-url:http://runtime-service:8081}")String runtimeUrl,
            @Value("${agent-studio.slack-mcp-url:http://slack-mcp:8080}")String slackMcpUrl){
        this.jdbc=jdbc;this.json=json;this.cipher=cipher;this.runtime=clients.clone().baseUrl(runtimeUrl).build();this.slackMcp=clients.clone().baseUrl(slackMcpUrl).build();
    }

    @PostMapping("/events")
    ResponseEntity<?> events(@RequestHeader("X-Slack-Request-Timestamp")String timestamp,@RequestHeader("X-Slack-Signature")String signature,@RequestBody String raw){
        Map<String,Object> payload=map(raw);String team=required(payload.get("team_id"),"team_id");SlackProfile profile=profile(team);verify(timestamp,signature,raw,profile.signingSecret());
        if("url_verification".equals(payload.get("type")))return ResponseEntity.ok(Map.of("challenge",required(payload.get("challenge"),"challenge")));
        if(!"event_callback".equals(payload.get("type")))return ResponseEntity.ok(Map.of("accepted",false,"reason","unsupported event type"));
        String eventId=required(payload.get("event_id"),"event_id");Map<String,Object> event=object(payload.get("event"));
        if(!"app_mention".equals(event.get("type"))||event.containsKey("bot_id"))return ResponseEntity.ok(Map.of("accepted",false,"reason","event ignored"));
        String channel=required(event.get("channel"),"channel"),ts=required(event.get("ts"),"ts"),thread=String.valueOf(event.getOrDefault("thread_ts",ts));
        int inserted=jdbc.sql("insert into slack_inbound_events(event_id,tenant_id,provider_id,channel_id,thread_ts) values(?,?,?,?,?) on conflict do nothing").params(eventId,profile.tenant(),profile.provider(),channel,thread).update();
        if(inserted==0)return ResponseEntity.ok(Map.of("accepted",true,"duplicate",true,"eventId",eventId));
        Map<String,Object> invocation=new LinkedHashMap<>();invocation.put("text",event.get("text"));invocation.put("channel",channel);invocation.put("ts",ts);invocation.put("threadTs",thread);invocation.put("eventId",eventId);invocation.put("user",event.getOrDefault("user","unknown"));invocation.put("_integration",profile.configuration());
        tasks.submit(()->dispatch(profile,eventId,invocation));
        return ResponseEntity.accepted().body(Map.of("accepted",true,"eventId",eventId));
    }

    private void dispatch(SlackProfile profile,String eventId,Map<String,Object> invocation){
        String channel=String.valueOf(invocation.get("channel")),thread=String.valueOf(invocation.get("threadTs"));
        try{
            Map<String,Object> parsed=slackMcp.post().uri("/tools/slack.agent.invoke").contentType(MediaType.APPLICATION_JSON).body(invocation).retrieve().body(Map.class);Map<String,Object> request=object(parsed.get("dispatch"));
            Map<String,Object> run=runtime.post().uri("/api/v1/runs").header("X-Tenant-Id",profile.tenant()).contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(Map.class);String runId=required(run.get("runId"),"runId");jdbc.sql("update slack_inbound_events set run_id=?,status='RUNNING',updated_at=now() where event_id=?").params(runId,eventId).update();
            Map<String,Object> completed=await(profile.tenant(),runId);String status=String.valueOf(completed.get("status"));if("COMPLETED".equals(status)){reply(profile,channel,thread,responseText(completed));jdbc.sql("update slack_inbound_events set status='COMPLETED',updated_at=now() where event_id=?").param(eventId).update();}else throw new IllegalStateException(String.valueOf(completed.getOrDefault("error","Agent run "+status)));
        }catch(Exception error){String message=safe(error);jdbc.sql("update slack_inbound_events set status='FAILED',error=?,updated_at=now() where event_id=?").params(message,eventId).update();try{reply(profile,channel,thread,"Agent invocation failed (`"+eventId+"`): "+message);}catch(Exception ignored){}}
    }
    private Map<String,Object> await(String tenant,String runId)throws InterruptedException{for(int attempt=0;attempt<600;attempt++){Map<String,Object> run=runtime.get().uri("/api/v1/runs/{id}",runId).header("X-Tenant-Id",tenant).retrieve().body(Map.class);String status=String.valueOf(run.get("status"));if(Set.of("COMPLETED","FAILED","CANCELLED","TIMED_OUT").contains(status))return run;Thread.sleep(1000);}throw new IllegalStateException("Agent run timed out while waiting for a Slack reply");}
    private void reply(SlackProfile profile,String channel,String thread,String text){Map<String,Object> body=new LinkedHashMap<>();body.put("channel",channel);body.put("threadTs",thread);body.put("finalResponse",text);body.put("_integration",profile.configuration());slackMcp.post().uri("/tools/slack.messages.send").header("X-Integration-Credential-botToken",profile.botToken()).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();}
    private SlackProfile profile(String workspace){return jdbc.sql("select cp.tenant_id,cp.provider_id,cp.configuration::text from capability_providers cp where cp.integration_kind='SLACK' and cp.enabled=true and cp.configuration->>'workspaceId'=? order by cp.updated_at desc limit 1").param(workspace).query((r,n)->{String tenant=r.getString(1),provider=r.getString(2);return new SlackProfile(tenant,provider,map(r.getString(3)),credential(tenant,provider,"botToken"),credential(tenant,provider,"signingSecret"));}).optional().orElseThrow(()->new IllegalArgumentException("No enabled Slack integration is registered for workspace"));}
    private String credential(String tenant,String provider,String key){return jdbc.sql("select us.ciphertext,us.initialization_vector,us.user_id,us.secret_id from capability_provider_secrets cps join user_secrets us on us.tenant_id=cps.tenant_id and cps.secret_ref=('dbsecret://' || us.user_id || '/' || us.secret_id) where cps.tenant_id=? and cps.provider_id=? and cps.credential_key=?").params(tenant,provider,key).query((r,n)->cipher.decrypt(r.getString(1),r.getString(2),tenant+":"+r.getString(3)+":"+r.getString(4))).optional().orElseThrow(()->new IllegalStateException("Slack "+key+" is not configured"));}
    private void verify(String timestamp,String signature,String body,String secret){try{long sent=Long.parseLong(timestamp);if(Math.abs(Instant.now().getEpochSecond()-sent)>300)throw new IllegalArgumentException("Stale Slack request");String base="v0:"+timestamp+":"+body;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));String expected="v0:"+HexFormat.of().formatHex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),signature.getBytes(StandardCharsets.UTF_8)))throw new IllegalArgumentException("Invalid Slack request signature");}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalStateException("Could not verify Slack request",e);}}
    private String responseText(Map<String,Object> run){Map<String,Object> output=object(run.get("output"));for(String key:List.of("finalResponse","response","message","text")){Object value=output.get(key);if(value!=null&&!String.valueOf(value).isBlank())return String.valueOf(value);}return output.isEmpty()?"Agent run completed.":String.valueOf(output);}
    private Map<String,Object> map(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalArgumentException("Invalid Slack payload",e);}}
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value){return value instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();}
    private static String required(Object value,String name){if(value==null||String.valueOf(value).isBlank())throw new IllegalArgumentException(name+" is required");return String.valueOf(value);}
    private static String safe(Exception error){String message=error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();return message.length()>900?message.substring(0,900):message;}
    record SlackProfile(String tenant,String provider,Map<String,Object> configuration,String botToken,String signingSecret){}
}
