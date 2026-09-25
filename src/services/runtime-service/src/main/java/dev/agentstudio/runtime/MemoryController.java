package dev.agentstudio.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/memory")
public class MemoryController {
    private final StringRedisTemplate redis; private final JdbcClient jdbc; private final ObjectMapper json;
    public MemoryController(StringRedisTemplate redis,JdbcClient jdbc,ObjectMapper json){this.redis=redis;this.jdbc=jdbc;this.json=json;}

    @PutMapping("/{namespace}/{key}")
    MemoryView put(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String namespace,@PathVariable String key,@RequestBody MemoryInput input){
        String t=required(tenant);String encoded=write(input.content());Tier tier=input.tier()==null?Tier.BOTH:input.tier();
        if(tier!=Tier.COLD)redis.opsForValue().set(redisKey(t,namespace,key),encoded,Duration.ofSeconds(input.ttlSeconds()==null?3600:input.ttlSeconds()));
        if(tier!=Tier.HOT)jdbc.sql("insert into cold_memory(tenant_id,namespace,memory_key,content,classification,expires_at) values(?,?,?,?::jsonb,?,case when ? is null then null else now()+(? * interval '1 second') end) on conflict(tenant_id,namespace,memory_key) do update set content=excluded.content,classification=excluded.classification,expires_at=excluded.expires_at,updated_at=now()")
          .params(t,namespace,key,encoded,Optional.ofNullable(input.classification()).orElse("INTERNAL"),input.ttlSeconds(),input.ttlSeconds()).update();
        return new MemoryView(namespace,key,tier,input.content(),tier!=Tier.COLD,tier!=Tier.HOT);
    }
    @GetMapping("/{namespace}/{key}")
    ResponseEntity<MemoryView> get(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String namespace,@PathVariable String key){
        String t=required(tenant);String hot=redis.opsForValue().get(redisKey(t,namespace,key));
        if(hot!=null)return ResponseEntity.ok(new MemoryView(namespace,key,Tier.HOT,read(hot),true,false));
        return jdbc.sql("select content from cold_memory where tenant_id=? and namespace=? and memory_key=? and (expires_at is null or expires_at>now())").params(t,namespace,key)
          .query((rs,row)->new MemoryView(namespace,key,Tier.COLD,read(rs.getString(1)),false,true)).optional().map(ResponseEntity::ok).orElseGet(()->ResponseEntity.notFound().build());
    }
    @DeleteMapping("/{namespace}/{key}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String namespace,@PathVariable String key){String t=required(tenant);redis.delete(redisKey(t,namespace,key));jdbc.sql("delete from cold_memory where tenant_id=? and namespace=? and memory_key=?").params(t,namespace,key).update();}
    private String redisKey(String t,String n,String k){return "memory:"+t+":"+n+":"+k;}
    private String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("X-Tenant-Id is required");return v;}
    private String write(Object v){try{return json.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private Map<String,Object> read(String v){try{return json.readValue(v,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    enum Tier{HOT,COLD,BOTH}
    record MemoryInput(Tier tier,Map<String,Object> content,Long ttlSeconds,String classification){}
    record MemoryView(String namespace,String key,Tier servedFrom,Map<String,Object> content,boolean hotPresent,boolean coldPresent){}
}
