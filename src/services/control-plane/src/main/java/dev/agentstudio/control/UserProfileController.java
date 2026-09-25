package dev.agentstudio.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user-profile")
public class UserProfileController {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public UserProfileController(JdbcClient jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @GetMapping ProfileView get(@RequestHeader("X-Tenant-Id") String tenant, @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user) {
        ensure(tenant,user);
        return jdbc.sql("select user_id,display_name,email,preferences,created_at,updated_at from user_profiles where tenant_id=? and user_id=?")
                .params(clean(tenant),clean(user)).query((rs,n)->new ProfileView(rs.getString(1),rs.getString(2),rs.getString(3),read(rs.getString(4)),rs.getTimestamp(5).toInstant(),rs.getTimestamp(6).toInstant())).single();
    }

    @PutMapping ProfileView update(@RequestHeader("X-Tenant-Id") String tenant, @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user, @RequestBody UpdateProfile request) {
        ensure(tenant,user);
        jdbc.sql("update user_profiles set display_name=?,email=?,preferences=?::jsonb,updated_at=now() where tenant_id=? and user_id=?")
                .params(required(request.displayName()),request.email(),write(request.preferences()==null?Map.of():request.preferences()),clean(tenant),clean(user)).update();
        return get(tenant,user);
    }

    @GetMapping("/configurations") Map<String,JsonNode> configurations(@RequestHeader("X-Tenant-Id") String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,@RequestParam(defaultValue="studio") String namespace) {
        ensure(tenant,user);
        return jdbc.sql("select config_key,config_value from user_configurations where tenant_id=? and user_id=? and namespace=? order by config_key")
                .params(clean(tenant),clean(user),clean(namespace)).query((rs,n)->Map.entry(rs.getString(1),read(rs.getString(2)))).list().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
    }

    @PutMapping("/configurations/{namespace}/{key}") JsonNode putConfiguration(@RequestHeader("X-Tenant-Id") String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,@PathVariable String namespace,@PathVariable String key,@RequestBody JsonNode value) {
        ensure(tenant,user);
        jdbc.sql("insert into user_configurations(tenant_id,user_id,namespace,config_key,config_value) values(?,?,?,?,?::jsonb) on conflict(tenant_id,user_id,namespace,config_key) do update set config_value=excluded.config_value,updated_at=now()")
                .params(clean(tenant),clean(user),clean(namespace),clean(key),write(value)).update();
        return value;
    }

    @DeleteMapping("/configurations/{namespace}/{key}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void deleteConfiguration(@RequestHeader("X-Tenant-Id") String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,@PathVariable String namespace,@PathVariable String key){
        jdbc.sql("delete from user_configurations where tenant_id=? and user_id=? and namespace=? and config_key=?").params(clean(tenant),clean(user),clean(namespace),clean(key)).update();
    }

    private void ensure(String tenant,String user){jdbc.sql("insert into user_profiles(tenant_id,user_id,display_name) values(?,?,?) on conflict do nothing").params(clean(tenant),clean(user),clean(user)).update();}
    private JsonNode read(String value){try{return json.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private static String clean(String value){if(value==null||value.isBlank()||value.length()>192)throw new IllegalArgumentException("invalid identifier");return value.trim();}
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("displayName is required");return value.trim();}
    record UpdateProfile(String displayName,String email,Map<String,Object> preferences){}
    record ProfileView(String userId,String displayName,String email,JsonNode preferences,Instant createdAt,Instant updatedAt){}
}
