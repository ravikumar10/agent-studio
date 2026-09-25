package dev.agentstudio.control;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.HttpHeaders;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/model-connections")
public class ModelConnectionController {
    private static final Map<Provider,String> DEFAULT_URLS=Map.of(
            Provider.OPENAI,"https://api.openai.com/v1",
            Provider.ANTHROPIC,"https://api.anthropic.com/v1");
    private final JdbcClient jdbc;
    private final SecretCipher cipher; private final RestClient.Builder clients;
    public ModelConnectionController(JdbcClient jdbc,SecretCipher cipher,RestClient.Builder clients){this.jdbc=jdbc;this.cipher=cipher;this.clients=clients;}

    @GetMapping List<ModelConnectionView> list(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user){
        return jdbc.sql("select connection_id,display_name,provider,base_url,secret_ref,organization_id,project_id,enabled,created_at,owner_user_id from model_connections where tenant_id=? and owner_user_id=? order by display_name")
                .params(new TenantContext(tenant).tenantId(),required(user,"X-User-Id")).query((rs,n)->new ModelConnectionView(rs.getString(1),rs.getString(2),Provider.valueOf(rs.getString(3)),rs.getString(4),maskedReference(tenant,rs.getString(10),rs.getString(5)),rs.getString(6),rs.getString(7),rs.getBoolean(8),rs.getTimestamp(9).toInstant(),rs.getString(10))).list();
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional ModelConnectionView create(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@RequestBody CreateModelConnection request){
        String tenantId=new TenantContext(tenant).tenantId();
        String id=required(request.connectionId(),"connectionId"); String name=required(request.displayName(),"displayName");
        Provider provider=Objects.requireNonNull(request.provider(),"provider");
        String baseUrl=Objects.requireNonNullElse(request.baseUrl(),DEFAULT_URLS.get(provider));
        if(baseUrl==null||!baseUrl.startsWith("https://"))throw new IllegalArgumentException("baseUrl must use HTTPS");
        validateProviderUrl(provider,baseUrl);
        String userId=required(user,"X-User-Id");
        jdbc.sql("insert into user_profiles(tenant_id,user_id,display_name) values(?,?,?) on conflict do nothing").params(tenantId,userId,userId).update();
        String secretRef;
        if(request.apiKey()!=null&&!request.apiKey().isBlank()){
            String key=request.apiKey().trim(); String secretId="model-"+id;
            SecretCipher.Encrypted encrypted=cipher.encrypt(key,tenantId+":"+userId+":"+secretId);
            jdbc.sql("insert into user_secrets(tenant_id,user_id,secret_id,secret_type,ciphertext,initialization_vector,last_four) values(?,?,?,?,?,?,?) on conflict(tenant_id,user_id,secret_id) do update set ciphertext=excluded.ciphertext,initialization_vector=excluded.initialization_vector,last_four=excluded.last_four,updated_at=now()")
                    .params(tenantId,userId,secretId,"MODEL_API_KEY",encrypted.ciphertext(),encrypted.initializationVector(),lastFour(key)).update();
            secretRef="dbsecret://"+userId+"/"+secretId;
        }else{
            secretRef=required(request.secretRef(),"apiKey or secretRef");
            if(!secretRef.startsWith("env://")&&!secretRef.startsWith("secret://"))throw new IllegalArgumentException("secretRef must use env:// or secret://");
        }
        Instant now=Instant.now();
        jdbc.sql("insert into model_connections(tenant_id,connection_id,display_name,provider,base_url,secret_ref,organization_id,project_id,enabled,created_at,updated_at,owner_user_id) values(?,?,?,?,?,?,?,?,true,?,?,?)")
                .params(tenantId,id,name,provider.name(),baseUrl,secretRef,request.organizationId(),request.projectId(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now),userId).update();
        return new ModelConnectionView(id,name,provider,baseUrl,maskedReference(tenantId,userId,secretRef),request.organizationId(),request.projectId(),true,now,userId);
    }

    @PostMapping("/verify")
    VerificationResult verify(@RequestBody VerifyModelConnection request){
        Provider provider=Objects.requireNonNull(request.provider(),"provider");
        if(provider==Provider.OPENAI_COMPATIBLE)return new VerificationResult(false,false,0,"Automatic verification is not available for custom compatible endpoints yet");
        String apiKey=required(request.apiKey(),"apiKey");
        String baseUrl=Objects.requireNonNullElse(request.baseUrl(),DEFAULT_URLS.get(provider));
        if(baseUrl==null||!baseUrl.startsWith("https://"))throw new IllegalArgumentException("baseUrl must use HTTPS");
        validateProviderUrl(provider,baseUrl);
        try{
            RestClient.Builder builder=clients.clone().baseUrl(baseUrl);
            JsonNode response;
            if(provider==Provider.OPENAI){
                builder.defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+apiKey);
                if(request.organizationId()!=null&&!request.organizationId().isBlank())builder.defaultHeader("OpenAI-Organization",request.organizationId());
                if(request.projectId()!=null&&!request.projectId().isBlank())builder.defaultHeader("OpenAI-Project",request.projectId());
                response=builder.build().get().uri("/models").retrieve().body(JsonNode.class);
            }else{
                response=builder.defaultHeader("x-api-key",apiKey).defaultHeader("anthropic-version","2023-06-01").build().get().uri("/models").retrieve().body(JsonNode.class);
            }
            int count=response==null?0:response.path("data").size();
            return new VerificationResult(true,true,count,"Connection verified; "+count+" models discovered");
        }catch(RestClientResponseException rejected){
            int status=rejected.getStatusCode().value();
            String message=(status==401||status==403)?"Provider rejected the API key":"Provider verification failed with HTTP "+status;
            return new VerificationResult(true,false,0,message);
        }catch(Exception unavailable){
            return new VerificationResult(true,false,0,"Provider could not be reached from the backend");
        }
    }

    @GetMapping("/{connectionId}/models")
    List<ModelOption> models(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@PathVariable String connectionId){
        String tenantId=new TenantContext(tenant).tenantId();
        StoredConnection c=jdbc.sql("select provider,base_url,secret_ref,organization_id,project_id from model_connections where tenant_id=? and owner_user_id=? and connection_id=? and enabled=true")
                .params(tenantId,user,connectionId).query((rs,n)->new StoredConnection(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5))).optional()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"model connection not found"));
        if("OPENAI_COMPATIBLE".equals(c.provider())) return List.of();
        RestClient.Builder builder=clients.clone().baseUrl(c.baseUrl());
        JsonNode response;
        try { if("OPENAI".equals(c.provider())){
            builder.defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+resolveSecret(tenantId,user,c.secretRef()));
            if(c.organizationId()!=null&&!c.organizationId().isBlank())builder.defaultHeader("OpenAI-Organization",c.organizationId());
            if(c.projectId()!=null&&!c.projectId().isBlank())builder.defaultHeader("OpenAI-Project",c.projectId());
            response=builder.build().get().uri("/models").retrieve().body(JsonNode.class);
        }else{
            response=builder.defaultHeader("x-api-key",resolveSecret(tenantId,user,c.secretRef())).defaultHeader("anthropic-version","2023-06-01").build().get().uri("/models").retrieve().body(JsonNode.class);
        }} catch(RestClientResponseException rejected){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,rejected.getStatusCode().value()==401?"Provider rejected the stored API key. Reconnect this provider with a valid key.":"Provider model discovery failed with HTTP "+rejected.getStatusCode().value());}
        List<ModelOption> result=new ArrayList<>();
        for(JsonNode item:response.path("data")){String id=item.path("id").asText();if(!id.isBlank())result.add(new ModelOption(id,item.path("display_name").asText(id)));}
        result.sort(Comparator.comparing(ModelOption::id)); return result;
    }

    @DeleteMapping("/{connectionId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    void delete(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@PathVariable String connectionId){
        String tenantId=new TenantContext(tenant).tenantId();
        Integer uses=jdbc.sql("select count(*) from model_profiles where tenant_id=? and spec->>'connectionId'=?").params(tenantId,connectionId).query(Integer.class).single();
        if(uses>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Delete the "+uses+" dependent model profile(s) first");
        String ref=jdbc.sql("select secret_ref from model_connections where tenant_id=? and owner_user_id=? and connection_id=?").params(tenantId,user,connectionId).query(String.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"model connection not found"));
        jdbc.sql("delete from model_connections where tenant_id=? and owner_user_id=? and connection_id=?").params(tenantId,user,connectionId).update();
        if(ref.startsWith("dbsecret://")){String secretId=ref.substring(ref.lastIndexOf('/')+1);jdbc.sql("delete from user_secrets where tenant_id=? and user_id=? and secret_id=?").params(tenantId,user,secretId).update();}
    }

    private static String required(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" is required");return value.trim();}
    private static void validateProviderUrl(Provider provider,String baseUrl){
        String normalized=baseUrl.replaceAll("/+$","");
        if(provider==Provider.OPENAI&&!DEFAULT_URLS.get(Provider.OPENAI).equals(normalized))throw new IllegalArgumentException("OpenAI verification must use https://api.openai.com/v1");
        if(provider==Provider.ANTHROPIC&&!DEFAULT_URLS.get(Provider.ANTHROPIC).equals(normalized))throw new IllegalArgumentException("Anthropic verification must use https://api.anthropic.com/v1");
    }
    private String maskedReference(String tenant,String user,String ref){
        if(ref.startsWith("dbsecret://")){String secretId=ref.substring(ref.lastIndexOf('/')+1);String last=jdbc.sql("select last_four from user_secrets where tenant_id=? and user_id=? and secret_id=?").params(new TenantContext(tenant).tenantId(),user,secretId).query(String.class).optional().orElse("");return "stored://••••"+last;}
        int marker=ref.indexOf("://");return marker<0?"configured":ref.substring(0,marker+3)+"••••••••";
    }
    private static String lastFour(String value){return value.substring(Math.max(0,value.length()-4));}
    private String resolveSecret(String tenant,String user,String ref){
        if(ref.startsWith("dbsecret://")){String secretId=ref.substring(ref.lastIndexOf('/')+1);var stored=jdbc.sql("select ciphertext,initialization_vector from user_secrets where tenant_id=? and user_id=? and secret_id=?").params(tenant,user,secretId).query((rs,n)->new String[]{rs.getString(1),rs.getString(2)}).optional().orElseThrow(()->new IllegalStateException("stored credential is unavailable"));return cipher.decrypt(stored[0],stored[1],tenant+":"+user+":"+secretId);}
        if(ref.startsWith("env://")){String value=System.getenv(ref.substring(6));if(value==null||value.isBlank())throw new IllegalStateException("environment credential is unavailable");return value;}
        throw new IllegalStateException("model discovery requires a stored or environment credential");
    }
    enum Provider { OPENAI, ANTHROPIC, OPENAI_COMPATIBLE }
    record CreateModelConnection(String connectionId,String displayName,Provider provider,String baseUrl,String apiKey,String secretRef,String organizationId,String projectId){}
    record VerifyModelConnection(Provider provider,String baseUrl,String apiKey,String organizationId,String projectId){}
    record VerificationResult(boolean supported,boolean valid,int modelCount,String message){}
    record ModelConnectionView(String connectionId,String displayName,Provider provider,String baseUrl,String secretRef,String organizationId,String projectId,boolean enabled,Instant createdAt,String ownerUserId){}
    record StoredConnection(String provider,String baseUrl,String secretRef,String organizationId,String projectId){}
    record ModelOption(String id,String displayName){}
}
