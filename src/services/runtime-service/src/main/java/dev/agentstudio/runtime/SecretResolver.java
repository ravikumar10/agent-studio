package dev.agentstudio.runtime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class SecretResolver {
    private final JdbcClient jdbc;
    private final SecretKeySpec key;
    public SecretResolver(JdbcClient jdbc,@Value("${agent-studio.secrets.encryption-key}") String material){
        this.jdbc=jdbc;
        if(material==null||material.length()<24)throw new IllegalStateException("AGENT_STUDIO_ENCRYPTION_KEY must contain at least 24 characters");
        try{key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)),"AES");}catch(Exception e){throw new IllegalStateException(e);}
    }
    String resolve(String tenant,String ref){
        if(ref.startsWith("env://")){String value=System.getenv(ref.substring(6));if(value==null||value.isBlank())throw new IllegalStateException("Configured model credential is unavailable");return value;}
        if(ref.startsWith("dbsecret://")){
            String path=ref.substring(11);int slash=path.indexOf('/');if(slash<1)throw new IllegalStateException("Invalid stored secret reference");
            String user=path.substring(0,slash),secretId=path.substring(slash+1);
            Stored stored=jdbc.sql("select ciphertext,initialization_vector from user_secrets where tenant_id=? and user_id=? and secret_id=?").params(tenant,user,secretId).query((rs,n)->new Stored(rs.getString(1),rs.getString(2))).optional().orElseThrow(()->new IllegalStateException("Stored model credential is unavailable"));
            try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(stored.iv)));cipher.updateAAD((tenant+":"+user+":"+secretId).getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.getDecoder().decode(stored.ciphertext)),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("Stored model credential could not be decrypted",e);}
        }
        throw new IllegalStateException("No secret adapter is configured for this credential reference");
    }
    Map<String,String> resolveProviderCredentials(String tenant,String provider){Map<String,String> values=new LinkedHashMap<>();jdbc.sql("select credential_key,secret_ref from capability_provider_secrets where tenant_id=? and provider_id=? order by credential_key").params(tenant,provider).query((rs,n)->Map.entry(rs.getString(1),rs.getString(2))).list().forEach(entry->values.put(entry.getKey(),resolve(tenant,entry.getValue())));return values;}
    record Stored(String ciphertext,String iv){}
}
