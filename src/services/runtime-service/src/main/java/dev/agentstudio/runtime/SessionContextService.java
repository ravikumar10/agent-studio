package dev.agentstudio.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Durable session memory with a disposable Redis hot projection. */
@Service
public class SessionContextService {
    private static final int MAX_TEXT=20_000;
    private final JdbcClient jdbc; private final StringRedisTemplate redis; private final ObjectMapper json;
    private final Duration hotTtl; private final int maxTurns; private final int maxEvidence;

    public SessionContextService(JdbcClient jdbc,StringRedisTemplate redis,ObjectMapper json,
            @Value("${agent-studio.memory.hot-ttl:PT24H}") Duration hotTtl,
            @Value("${agent-studio.memory.max-turns:20}") int maxTurns,
            @Value("${agent-studio.memory.max-evidence:8}") int maxEvidence){
        this.jdbc=jdbc;this.redis=redis;this.json=json;this.hotTtl=hotTtl;this.maxTurns=maxTurns;this.maxEvidence=maxEvidence;
    }

    public void open(String tenant,String session,String agent,String version,String subject){
        int inserted=jdbc.sql("insert into agent_sessions(tenant_id,session_id,agent_id,initial_agent_version,subject_id,status,memory_policy,expires_at) values(?,?,?,?,?,'ACTIVE','DURABLE',now()+interval '30 days') on conflict do nothing")
          .params(tenant,session,agent,version,subject).update();
        if(inserted==0){
            SessionOwner owner=jdbc.sql("select agent_id,subject_id from agent_sessions where tenant_id=? and session_id=?")
              .params(tenant,session).query((rs,n)->new SessionOwner(rs.getString(1),rs.getString(2))).single();
            if(!owner.agentId().equals(agent)||(!owner.subjectId().equals(subject)&&!"legacy".equals(owner.subjectId())))throw new IllegalArgumentException("Session does not belong to this agent and subject");
            if("legacy".equals(owner.subjectId()))jdbc.sql("update agent_sessions set subject_id=? where tenant_id=? and session_id=? and subject_id='legacy'").params(subject,tenant,session).update();
            jdbc.sql("update agent_sessions set status='ACTIVE',last_activity_at=now(),expires_at=now()+interval '30 days' where tenant_id=? and session_id=?")
              .params(tenant,session).update();
        }
    }

    public Context load(String tenant,String session){
        String cached=null;
        try{cached=redis.opsForValue().get(key(tenant,session));}catch(RuntimeException ignored){}
        if(cached!=null){Context value=read(cached,Context.class);return new Context(value.conversation(),value.evidence(),"REDIS",true);}
        List<Map<String,Object>> turns=jdbc.sql("select role,content from session_turns where tenant_id=? and session_id=? order by created_at desc limit ?")
          .params(tenant,session,maxTurns).query((rs,n)->turn(rs.getString(1),map(rs.getString(2)))).list();
        Collections.reverse(turns);
        List<Map<String,Object>> evidence=jdbc.sql("select capability_id,content,content_hash from session_evidence where tenant_id=? and session_id=? and (expires_at is null or expires_at>now()) order by created_at desc limit ?")
          .params(tenant,session,maxEvidence).query((rs,n)->Map.<String,Object>of("capability",Objects.toString(rs.getString(1),"unknown"),"content",map(rs.getString(2)),"contentHash",rs.getString(3))).list();
        Context context=new Context(List.copyOf(turns),List.copyOf(evidence),"POSTGRESQL",false);cache(tenant,session,context);
        return context;
    }

    public void userTurn(String tenant,String session,String run,Map<String,Object> input){
        Map<String,Object> safe=new LinkedHashMap<>(RunTraceSanitizer.map(input));safe.remove("conversation");
        insertTurn(tenant,session,run,"USER",safe);invalidate(tenant,session);
    }

    public void assistantTurn(String tenant,String session,String run,Map<String,Object> output){
        Map<String,Object> safe=RunTraceSanitizer.map(output);
        Map<String,Object> content=new LinkedHashMap<>();
        Object answer=safe.getOrDefault("answer",safe.getOrDefault("message",""));content.put("text",bounded(answer));
        for(String field:List.of("response","model","provider","groundingPolicy"))if(safe.containsKey(field))content.put(field,bounded(safe.get(field)));
        insertTurn(tenant,session,run,"ASSISTANT",content);
        evidence(tenant,session,run,output);
        jdbc.sql("update agent_sessions set last_activity_at=now() where tenant_id=? and session_id=?").params(tenant,session).update();
        invalidate(tenant,session);
    }

    public void evidence(String tenant,String session,String run,Map<String,Object> output){
        Object results=output.get("toolResults");
        if(results instanceof Collection<?> steps)for(Object value:steps){Map<String,Object> step=value instanceof Map<?,?> raw?cast(raw):json.convertValue(value,new TypeReference<>(){});saveEvidence(tenant,session,run,RunTraceSanitizer.map(step));}
        invalidate(tenant,session);
    }

    public void failure(String tenant,String session,String run,String error){
        insertTurn(tenant,session,run,"ERROR",Map.of("error",bounded(error)));
        jdbc.sql("update agent_sessions set last_activity_at=now() where tenant_id=? and session_id=?").params(tenant,session).update();invalidate(tenant,session);
    }

    public Map<String,Object> enrich(Map<String,Object> input,Context context){
        Map<String,Object> result=new LinkedHashMap<>(input);result.put("conversation",context.conversation());
        if(!context.evidence().isEmpty())result.put("sessionEvidence",context.evidence());
        result.put("sessionContextSource",context.source());return Map.copyOf(result);
    }

    private void saveEvidence(String tenant,String session,String run,Map<String,Object> step){
        Object response=step.getOrDefault("output",step.get("response"));if(response==null)return;
        Map<String,Object> content=Map.of("request",bounded(step.get("request")),"response",bounded(response));String encoded=write(content);String hash=sha256(encoded);
        jdbc.sql("insert into session_evidence(tenant_id,session_id,evidence_id,run_id,capability_id,content,content_hash,expires_at) values(?,?,?::uuid,?,?,?::jsonb,?,now()+interval '30 days') on conflict(tenant_id,session_id,content_hash) do nothing")
          .params(tenant,session,UUID.randomUUID().toString(),run,Objects.toString(step.get("capability"),null),encoded,hash).update();
    }
    private void insertTurn(String tenant,String session,String run,String role,Map<String,Object> content){jdbc.sql("insert into session_turns(tenant_id,session_id,turn_id,run_id,role,content) values(?,?,?::uuid,?,?,?::jsonb)").params(tenant,session,UUID.randomUUID().toString(),run,role,write(content)).update();}
    private Map<String,Object> turn(String role,Map<String,Object> content){Map<String,Object> value=new LinkedHashMap<>();value.put("role",role.toLowerCase(Locale.ROOT));value.putAll(content);if(!value.containsKey("text")){Object text=content.getOrDefault("message",content.getOrDefault("question",content.getOrDefault("answer","")));value.put("text",bounded(text));}return Map.copyOf(value);}
    private Object bounded(Object value){if(value==null)return "";if(value instanceof String text)return text.substring(0,Math.min(text.length(),MAX_TEXT));String encoded=write(value);return encoded.length()<=MAX_TEXT?value:Map.of("truncated",true,"preview",encoded.substring(0,MAX_TEXT));}
    private void cache(String tenant,String session,Context context){try{redis.opsForValue().set(key(tenant,session),write(context),hotTtl);}catch(RuntimeException ignored){}}
    private void invalidate(String tenant,String session){try{redis.delete(key(tenant,session));}catch(RuntimeException ignored){}}
    private String key(String tenant,String session){return "session-context:"+safe(tenant)+":"+safe(session);}
    private String safe(String value){return value.replaceAll("[^a-zA-Z0-9._-]","_");}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw new IllegalStateException(error);}}
    private <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception error){throw new IllegalStateException(error);}}
    private Map<String,Object> map(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception error){throw new IllegalStateException(error);}}
    @SuppressWarnings("unchecked") private Map<String,Object> cast(Map<?,?> value){return (Map<String,Object>)value;}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}}
    public record Context(List<Map<String,Object>> conversation,List<Map<String,Object>> evidence,String source,boolean hotHit){}
    private record SessionOwner(String agentId,String subjectId){}
}
