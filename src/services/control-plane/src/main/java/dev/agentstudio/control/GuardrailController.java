package dev.agentstudio.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/guardrails")
public class GuardrailController {
    private static final Set<String> ENFORCEMENT=Set.of("BLOCK","WARN","REDACT"),PHASES=Set.of("INPUT","TOOL","OUTPUT","BOTH");
    private final JdbcClient jdbc;private final ObjectMapper json;
    GuardrailController(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    @GetMapping List<View> list(@RequestHeader("X-Tenant-Id")String tenant){return jdbc.sql("select guardrail_id,display_name,description,guardrail_type,enforcement,phase,instruction,configuration::text,enabled from guardrails where tenant_id=? order by display_name").param(required(tenant,"tenant")).query((r,n)->new View(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),map(r.getString(8)),r.getBoolean(9),"guardrail://"+r.getString(1))).list();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional View create(@RequestHeader("X-Tenant-Id")String tenant,@RequestBody Save q){String t=required(tenant,"tenant"),id=id(q.guardrailId());jdbc.sql("insert into guardrails(tenant_id,guardrail_id,display_name,description,guardrail_type,enforcement,phase,instruction,configuration,enabled) values(?,?,?,?,?,?,?,?,?::jsonb,?)").params(t,id,required(q.displayName(),"displayName"),or(q.description(),""),required(q.guardrailType(),"guardrailType"),one(q.enforcement(),ENFORCEMENT,"enforcement"),one(q.phase(),PHASES,"phase"),or(q.instruction(),""),write(q.configuration()),q.enabled()==null||q.enabled()).update();return find(t,id);}
    @PutMapping("/{id}") @Transactional View update(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String id,@RequestBody Save q){String t=required(tenant,"tenant");int changed=jdbc.sql("update guardrails set display_name=?,description=?,guardrail_type=?,enforcement=?,phase=?,instruction=?,configuration=?::jsonb,enabled=?,updated_at=now() where tenant_id=? and guardrail_id=?").params(required(q.displayName(),"displayName"),or(q.description(),""),required(q.guardrailType(),"guardrailType"),one(q.enforcement(),ENFORCEMENT,"enforcement"),one(q.phase(),PHASES,"phase"),or(q.instruction(),""),write(q.configuration()),q.enabled()==null||q.enabled(),t,id).update();if(changed==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"guardrail not found");return find(t,id);}
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void delete(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String id){jdbc.sql("delete from guardrails where tenant_id=? and guardrail_id=?").params(required(tenant,"tenant"),id).update();}
    private View find(String t,String id){return list(t).stream().filter(v->v.guardrailId().equals(id)).findFirst().orElseThrow();}
    private Map<String,Object> map(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private String write(Object value){try{return json.writeValueAsString(value==null?Map.of():value);}catch(Exception e){throw new IllegalArgumentException("configuration must be JSON",e);}}
    private static String id(String value){String id=required(value,"guardrailId").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+","-").replaceAll("(^-|-$)","");if(id.isBlank())throw new IllegalArgumentException("guardrailId is invalid");return id;}
    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}private static String or(String v,String d){return v==null?d:v;}private static String one(String value,Set<String> allowed,String field){String v=required(value,field).toUpperCase(Locale.ROOT);if(!allowed.contains(v))throw new IllegalArgumentException(field+" is invalid");return v;}
    record Save(String guardrailId,String displayName,String description,String guardrailType,String enforcement,String phase,String instruction,Map<String,Object> configuration,Boolean enabled){}
    record View(String guardrailId,String displayName,String description,String guardrailType,String enforcement,String phase,String instruction,Map<String,Object> configuration,boolean enabled,String reference){}
}
