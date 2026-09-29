package dev.agentstudio.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/registries")
public class RegistryController {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final RegistryPromotionService promotions;
    private final SecretCipher cipher;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public RegistryController(JdbcClient jdbc, ObjectMapper json,RegistryPromotionService promotions,SecretCipher cipher) { this.jdbc=jdbc; this.json=json;this.promotions=promotions;this.cipher=cipher; }

    @GetMapping
    List<RegistryView> list(@RequestHeader("X-Tenant-Id") String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@RequestParam(required=false) RegistryType type) {
        String t=required(tenant);
        if(type==null)return jdbc.sql("select * from registries where tenant_id=? and owner_user_id=? order by registry_type,registry_id").params(t,user).query(this::map).list();
        return jdbc.sql("select * from registries where tenant_id=? and owner_user_id=? and registry_type=? order by registry_id").params(t,user,type.name()).query(this::map).list();
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    RegistryView create(@RequestHeader("X-Tenant-Id") String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@RequestBody RegistryInput r) {
        String t=required(tenant); validateGithubUri(r.sourceUri());
        jdbc.sql("insert into registries(tenant_id,registry_id,registry_type,display_name,source_type,source_uri,owner,discovery_pattern,status,metadata,owner_user_id) values(?,?,?,?,?,?,?,?,?,?::jsonb,?)")
          .params(t,r.registryId(),r.registryType().name(),r.displayName(),"GITHUB",r.sourceUri(),r.owner(),"catalog.json","ACTIVE",write(r.metadata()),user).update();
        if(r.accessToken()!=null&&!r.accessToken().isBlank())storeToken(t,user,r.registryId(),r.accessToken());
        return find(t,r.registryId());
    }

    @PutMapping("/{registryId}/credential") @ResponseStatus(HttpStatus.NO_CONTENT)
    void credential(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@PathVariable String registryId,@RequestBody RegistryCredential input){
        String t=required(tenant);find(t,registryId);if(input.accessToken()==null||input.accessToken().isBlank())throw new IllegalArgumentException("GitHub access token is required");storeToken(t,user,registryId,input.accessToken());
    }

    @PostMapping("/{registryId}/sync")
    SyncResult sync(@RequestHeader("X-Tenant-Id") String tenant,@PathVariable String registryId) {
        String t=required(tenant); RegistryView registry=find(t,registryId);
        jdbc.sql("update registries set sync_status='SYNCING',sync_error=null,updated_at=now() where tenant_id=? and registry_id=?").params(t,registryId).update();
        try{
            Catalog catalog=readUrl(t,registry,rawUrl(registry,"catalog.json"), Catalog.class);int discovered=0;
            for(CatalogArtifact a: catalog.artifacts()) {if(a.type()!=registry.registryType())continue;safePath(a.path());jdbc.sql("insert into registry_artifacts(tenant_id,registry_id,artifact_id,artifact_type,name,version,path,description,state,synced_at) values(?,?,?,?,?,?,?,?, 'DISCOVERED',now()) on conflict(tenant_id,registry_id,artifact_id,version) do update set name=excluded.name,path=excluded.path,description=excluded.description,synced_at=now()").params(t,registryId,a.id(),a.type().name(),a.name(),a.version(),a.path(),a.description()==null?"":a.description()).update();discovered++;}
            jdbc.sql("update registries set sync_status='SYNCED',sync_error=null,last_synced_at=now(),updated_at=now() where tenant_id=? and registry_id=?").params(t,registryId).update();
            return new SyncResult(registryId,catalog.schemaVersion(),discovered);
        }catch(RuntimeException e){jdbc.sql("update registries set sync_status='FAILED',sync_error=?,last_synced_at=now(),updated_at=now() where tenant_id=? and registry_id=?").params(safeError(e),t,registryId).update();throw e;}
    }

    @GetMapping("/{registryId}/artifacts")
    List<ArtifactView> artifacts(@RequestHeader("X-Tenant-Id") String tenant,@PathVariable String registryId) {
        return jdbc.sql("select artifact_id,artifact_type,name,version,path,description,state,pulled_at,promoted_at,promotion_error from registry_artifacts where tenant_id=? and registry_id=? order by artifact_id")
          .params(required(tenant),registryId).query((rs,n)->new ArtifactView(rs.getString(1),RegistryType.valueOf(rs.getString(2)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),instant(rs.getTimestamp(8)),instant(rs.getTimestamp(9)),rs.getString(10))).list();
    }

    @PostMapping("/{registryId}/artifacts/{artifactId}/pull")
    ArtifactView pull(@RequestHeader("X-Tenant-Id") String tenant,@PathVariable String registryId,@PathVariable String artifactId) {
        String t=required(tenant); RegistryView registry=find(t,registryId);
        ArtifactView a=jdbc.sql("select artifact_id,artifact_type,name,version,path,description,state,pulled_at,promoted_at,promotion_error from registry_artifacts where tenant_id=? and registry_id=? and artifact_id=? order by version desc limit 1")
          .params(t,registryId,artifactId).query((rs,n)->new ArtifactView(rs.getString(1),RegistryType.valueOf(rs.getString(2)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),instant(rs.getTimestamp(8)),instant(rs.getTimestamp(9)),rs.getString(10))).single();
        String content=readText(t,registry,rawUrl(registry,a.path()));
        content=enrich(t,registry,a,content);
        jdbc.sql("update registry_artifacts set content=?,state='PULLED',pulled_at=now() where tenant_id=? and registry_id=? and artifact_id=? and version=?")
          .params(content,t,registryId,artifactId,a.version()).update();
        return new ArtifactView(a.id(),a.type(),a.name(),a.version(),a.path(),a.description(),"PULLED",java.time.Instant.now().toString(),a.promotedAt(),null);
    }

    @PostMapping("/{registryId}/artifacts/{artifactId}/promote")
    RegistryPromotionService.PromotionResult promote(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@PathVariable String registryId,@PathVariable String artifactId){String t=required(tenant);RegistryView registry=find(t,registryId);ArtifactContent artifact=jdbc.sql("select artifact_id,artifact_type,name,version,path,description,content from registry_artifacts where tenant_id=? and registry_id=? and artifact_id=? order by version desc limit 1").params(t,registryId,artifactId).query((rs,n)->new ArtifactContent(rs.getString(1),RegistryType.valueOf(rs.getString(2)),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7))).single();if(artifact.content()==null||artifact.content().isBlank())throw new IllegalArgumentException("Pull the artifact before promotion");try{return promotions.promote(t,user,registryId,registry.sourceUri(),artifact);}catch(RuntimeException error){jdbc.sql("update registry_artifacts set state='FAILED',promotion_error=? where tenant_id=? and registry_id=? and artifact_id=? and version=?").params(safeError(error),t,registryId,artifact.id(),artifact.version()).update();throw error;}}

    @DeleteMapping("/{registryId}/artifacts/{artifactId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteArtifact(@RequestHeader("X-Tenant-Id")String tenant,@PathVariable String registryId,@PathVariable String artifactId){jdbc.sql("delete from registry_artifacts where tenant_id=? and registry_id=? and artifact_id=?").params(required(tenant),registryId,artifactId).update();}

    @DeleteMapping("/{registryId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRegistry(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@PathVariable String registryId){String t=required(tenant);jdbc.sql("delete from registries where tenant_id=? and owner_user_id=? and registry_id=?").params(t,user,registryId).update();jdbc.sql("delete from user_secrets where tenant_id=? and user_id=? and secret_id=?").params(t,user,secretId(registryId)).update();}

    private RegistryView find(String tenant,String id){return jdbc.sql("select * from registries where tenant_id=? and registry_id=?").params(tenant,id).query(this::map).single();}
    private RegistryView map(java.sql.ResultSet rs,int row)throws java.sql.SQLException{return new RegistryView(rs.getString("registry_id"),RegistryType.valueOf(rs.getString("registry_type")),rs.getString("display_name"),rs.getString("source_type"),rs.getString("source_uri"),rs.getString("owner"),rs.getString("discovery_pattern"),rs.getString("status"),read(rs.getString("metadata")),rs.getString("sync_status"),rs.getTimestamp("last_synced_at")==null?null:rs.getTimestamp("last_synced_at").toInstant().toString(),rs.getString("sync_error"));}
    private URI rawUrl(RegistryView r,String path){validateGithubUri(r.sourceUri());safePath(path);URI u=URI.create(r.sourceUri());String[] p=u.getPath().replaceFirst("^/","").split("/");if(p.length!=2)throw new IllegalArgumentException("GitHub source must be https://github.com/{owner}/{repository}");String branch=String.valueOf(r.metadata().getOrDefault("branch","main"));return URI.create("https://raw.githubusercontent.com/"+p[0]+"/"+p[1]+"/"+branch+"/"+path);}
    private void validateGithubUri(String source){URI u=URI.create(source);if(!"https".equals(u.getScheme())||!"github.com".equalsIgnoreCase(u.getHost()))throw new IllegalArgumentException("Only https://github.com repository URLs are supported");}
    private void safePath(String path){if(path==null||path.isBlank()||path.startsWith("/")||path.contains(".."))throw new IllegalArgumentException("Unsafe artifact path");}
    private String readText(String tenant,RegistryView registry,URI uri){try{HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Accept","application/vnd.github.raw+json,application/json,text/plain");token(tenant,registry.registryId()).ifPresent(value->request.header("Authorization","Bearer "+value));HttpResponse<String> r=http.send(request.GET().build(),HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new IllegalArgumentException("Repository returned HTTP "+r.statusCode());if(r.body().length()>1_000_000)throw new IllegalArgumentException("Repository artifact exceeds 1 MB");return r.body();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Repository request interrupted",e);}catch(java.io.IOException e){throw new IllegalArgumentException("Cannot read repository: "+e.getMessage(),e);}}
    private String enrich(String tenant,RegistryView registry,ArtifactView artifact,String content){if(artifact.type()!=RegistryType.MCP)return content;try{Map<String,Object> manifest=json.readValue(content,new TypeReference<>(){});Object schema=manifest.get("configurationSchema");if(schema!=null&&!String.valueOf(schema).isBlank()){safePath(String.valueOf(schema));manifest.put("_configurationSchema",json.readValue(readText(tenant,registry,rawUrl(registry,String.valueOf(schema))),new TypeReference<Map<String,Object>>(){}));}return json.writeValueAsString(manifest);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid MCP manifest",e);}}
    private <T>T readUrl(String tenant,RegistryView registry,URI uri,Class<T> type){try{return json.readValue(readText(tenant,registry,uri),type);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid registry manifest",e);}}
    private void storeToken(String tenant,String user,String registryId,String token){String sid=secretId(registryId);SecretCipher.Encrypted encrypted=cipher.encrypt(token.trim(),tenant+":"+user+":"+sid);jdbc.sql("insert into user_profiles(tenant_id,user_id,display_name) values(?,?,?) on conflict do nothing").params(tenant,user,user).update();jdbc.sql("insert into user_secrets(tenant_id,user_id,secret_id,secret_type,ciphertext,initialization_vector,last_four) values(?,?,?,?,?,?,?) on conflict(tenant_id,user_id,secret_id) do update set ciphertext=excluded.ciphertext,initialization_vector=excluded.initialization_vector,last_four=excluded.last_four,updated_at=now()").params(tenant,user,sid,"REGISTRY_GITHUB_TOKEN",encrypted.ciphertext(),encrypted.initializationVector(),lastFour(token.trim())).update();}
    private java.util.Optional<String> token(String tenant,String registryId){String sid=secretId(registryId);return jdbc.sql("select us.ciphertext,us.initialization_vector,us.user_id from registries r join user_secrets us on us.tenant_id=r.tenant_id and us.user_id=r.owner_user_id and us.secret_id=? where r.tenant_id=? and r.registry_id=? and r.source_type='GITHUB'").params(sid,tenant,registryId).query((rs,n)->cipher.decrypt(rs.getString(1),rs.getString(2),tenant+":"+rs.getString(3)+":"+sid)).optional();}
    private static String secretId(String registryId){return "registry-"+registryId+"-github-token";}
    private static String lastFour(String value){return value.length()<=4?value:value.substring(value.length()-4);}
    private String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("X-Tenant-Id is required");return v;}
    private String write(Object v){try{return json.writeValueAsString(v==null?Map.of():v);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private Map<String,Object> read(String v){try{return json.readValue(v,new TypeReference<>(){});}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private String safeError(Exception e){String value=e.getMessage()==null?"Synchronization failed":e.getMessage();return value.substring(0,Math.min(value.length(),1000));}
    private static String instant(java.sql.Timestamp value){return value==null?null:value.toInstant().toString();}

    public enum RegistryType{AGENT,MCP,SKILL}
    public record RegistryInput(String registryId,RegistryType registryType,String displayName,String sourceUri,String owner,Map<String,Object> metadata,String accessToken){}
    public record RegistryCredential(String accessToken){}
    public record RegistryView(String registryId,RegistryType registryType,String displayName,String sourceType,String sourceUri,String owner,String discoveryPattern,String status,Map<String,Object> metadata,String syncStatus,String lastSyncedAt,String syncError){}
    public record Catalog(String schemaVersion,List<CatalogArtifact> artifacts){public Catalog{artifacts=artifacts==null?List.of():List.copyOf(artifacts);}}
    public record CatalogArtifact(String id,RegistryType type,String name,String version,String path,String description){}
    public record SyncResult(String registryId,String schemaVersion,int discovered){}
    public record ArtifactView(String id,RegistryType type,String name,String version,String path,String description,String state,String pulledAt,String promotedAt,String promotionError){}
    public record ArtifactContent(String id,RegistryType type,String name,String version,String path,String description,String content){}
}
