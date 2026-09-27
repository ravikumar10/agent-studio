package dev.agentstudio.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Resolves only registry-synchronized skill content; runtime execution never fetches mutable remote files. */
@Component
class SkillContextResolver {
    private final JdbcClient jdbc; private final ObjectMapper json;
    SkillContextResolver(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    List<Map<String,String>> resolve(String tenant,String promptRef){
        List<String> references=references(promptRef);List<Map<String,String>> result=new ArrayList<>();int remaining=24_000;
        for(String reference:references){if(remaining<=0)break;Optional<Skill> found=find(tenant,reference);if(found.isEmpty())continue;Skill skill=found.get();String content=skill.content()==null?"":skill.content();if(content.length()>remaining)content=content.substring(0,remaining);remaining-=content.length();result.add(Map.of("id",skill.id(),"reference",reference,"description",Objects.toString(skill.description(),""),"content",content));}
        for(String reference:guardrailReferences(promptRef)){if(remaining<=0)break;Optional<Guardrail> found=guardrail(tenant,reference);if(found.isEmpty())continue;Guardrail guardrail=found.get();String content="MANDATORY GUARDRAIL ["+guardrail.enforcement()+" / "+guardrail.phase()+"]\n"+guardrail.instruction()+"\nConfiguration: "+guardrail.configuration();if(content.length()>remaining)content=content.substring(0,remaining);remaining-=content.length();result.add(Map.of("id",guardrail.id(),"reference",reference,"description","Mandatory "+guardrail.type()+" guardrail","content",content));}return List.copyOf(result);
    }

    private Optional<Skill> find(String tenant,String reference){
        String id=id(reference),path=path(reference);
        Optional<Skill> artifact=jdbc.sql("select artifact_id,description,coalesce(content,'') from registry_artifacts where tenant_id=? and artifact_type='SKILL' and (artifact_id=? or path=? or path like ?) and state in ('PULLED','PROMOTED') order by synced_at desc limit 1")
                .params(tenant,id,path,"%/"+path).query((rs,n)->new Skill(rs.getString(1),rs.getString(2),rs.getString(3))).optional();
        if(artifact.isPresent())return artifact;
        return jdbc.sql("select skill_id,description,'' from available_skills where tenant_id=? and (skill_id=? or reference=?) limit 1").params(tenant,id,reference).query((rs,n)->new Skill(rs.getString(1),rs.getString(2),rs.getString(3))).optional();
    }

    private List<String> references(String promptRef){
        if(promptRef==null||promptRef.isBlank())return List.of();
        if(promptRef.startsWith("skills://"))try{return json.readValue(URLDecoder.decode(promptRef.substring(9),StandardCharsets.UTF_8),new TypeReference<>(){});}catch(Exception ignored){return List.of();}
        if(promptRef.startsWith("plan://"))try{Map<String,Object> plan=json.readValue(URLDecoder.decode(promptRef.substring(7),StandardCharsets.UTF_8),new TypeReference<>(){});Object values=plan.get("skills");if(values instanceof Collection<?> collection)return collection.stream().map(String::valueOf).toList();return List.of();}catch(Exception ignored){return List.of();}
        return List.of(promptRef);
    }
    private List<String> guardrailReferences(String promptRef){if(promptRef!=null&&promptRef.startsWith("plan://"))try{Map<String,Object> plan=json.readValue(URLDecoder.decode(promptRef.substring(7),StandardCharsets.UTF_8),new TypeReference<>(){});Object values=plan.get("guardrails");if(values instanceof Collection<?> collection)return collection.stream().map(String::valueOf).toList();}catch(Exception ignored){}return List.of();}
    private Optional<Guardrail> guardrail(String tenant,String reference){String id=reference.replace("guardrail://","");return jdbc.sql("select guardrail_id,guardrail_type,enforcement,phase,instruction,configuration::text from guardrails where tenant_id=? and guardrail_id=? and enabled=true").params(tenant,id).query((r,n)->new Guardrail(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6))).optional();}
    private static String path(String reference){if(reference.startsWith("github://")){String value=reference.substring(9);String[] parts=value.split("/",3);return parts.length==3?parts[2]:value;}return reference.substring(reference.lastIndexOf('/')+1);}
    private static String id(String reference){String value=reference;int marker=value.indexOf("/skills/");if(marker>=0)value=value.substring(marker+8);if(value.endsWith("/SKILL.md"))value=value.substring(0,value.length()-9);if(value.contains("/"))value=value.substring(value.lastIndexOf('/')+1);return value.replace("SKILL.md","");}
    private record Skill(String id,String description,String content){}
    private record Guardrail(String id,String type,String enforcement,String phase,String instruction,String configuration){}
}
