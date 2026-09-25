package dev.agentstudio.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentCreatorService {
    private static final Pattern NAMED=Pattern.compile("(?i)(?:called|named|name(?:d)?|agent)\\s+[\"']?([a-z][a-z0-9 _-]{2,48})");
    private final JdbcClient jdbc; private final ObjectMapper json;
    public AgentCreatorService(JdbcClient jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}

    @Transactional
    public CreatedAgent create(String tenant,Map<String,Object> input){
        String request=Objects.toString(input.getOrDefault("message",input.getOrDefault("question","")),"").trim();
        if(request.length()<8)throw new IllegalArgumentException("Describe the agent's purpose, inputs, and expected result.");
        String name=name(request);String base=slug(name);String id=unique(tenant,base);String profile=defaultProfile(tenant);
        Set<String> tools=tools(request);List<String> skills=skills(request);boolean multi=contains(request,"multi-agent","multiple agents","team of agents");
        String interaction=contains(request,"chat","conversation","assistant")?"TASK_AND_CHAT":"TASK";Instant now=Instant.now();
        String tags=write(Set.of("agent-created","composed"));
        jdbc.sql("insert into agents(tenant_id,id,display_name,description,owner_team,tags,interaction_mode,topology,trigger_mode,status,created_at,updated_at) values(?,?,?,?,?,?::jsonb,?,?,?,?,?,?)")
                .params(tenant,id,name,request,"platform",tags,interaction,multi?"MULTI_AGENT":"SINGLE_AGENT","ON_DEMAND","ACTIVE",java.sql.Timestamp.from(now),java.sql.Timestamp.from(now)).update();
        Map<String,Object> spec=new LinkedHashMap<>();spec.put("tenantId",tenant);spec.put("agentId",id);spec.put("version","1.0.0");spec.put("runtimeType","CONFIG");spec.put("hostingMode","PLATFORM");spec.put("artifactRef","config://"+id+"/1.0.0");spec.put("remoteEndpointRef",null);spec.put("capabilitiesProvided",Set.of("agent."+id+".invoke"));spec.put("toolCapabilitiesRequired",tools);spec.put("agentCapabilitiesRequired",Set.of());spec.put("modelProfile",profile);spec.put("promptRef",skills.isEmpty()?"catalog://skills/task-planning":"skills://"+java.net.URLEncoder.encode(write(skills),java.nio.charset.StandardCharsets.UTF_8));spec.put("inputSchemaRef","catalog://schemas/"+id+"-input");spec.put("outputSchemaRef","catalog://schemas/"+id+"-output");spec.put("executionPolicyRef","policy://default-bounded");spec.put("securityPolicyRef","policy://tenant-default");spec.put("checksum","agent-creator-"+UUID.randomUUID());spec.put("lifecycle","ACTIVE");spec.put("createdAt",now.toString());
        jdbc.sql("insert into agent_versions(tenant_id,agent_id,version,spec,lifecycle,checksum,created_at) values(?,?,?,?::jsonb,'ACTIVE',?,?)")
                .params(tenant,id,"1.0.0",write(spec),spec.get("checksum"),java.sql.Timestamp.from(now)).update();
        jdbc.sql("insert into agent_release_state(tenant_id,agent_id,active_version,updated_at) values(?,?,?,?)")
                .params(tenant,id,"1.0.0",java.sql.Timestamp.from(now)).update();
        return new CreatedAgent(id,name,profile,tools,skills,"Created "+name+" and published version 1.0.0. It is visible in the agent catalog and ready to run. Use Configure to review or change its model, MCP tools, and skills.");
    }
    private String defaultProfile(String tenant){return jdbc.sql("select profile_id from model_profiles where tenant_id=? order by case when coalesce(spec->>'connectionId','')<>'' then 0 when profile_id='balanced-text' then 1 else 2 end,created_at limit 1").param(tenant).query(String.class).optional().orElseThrow(()->new IllegalStateException("Create a model profile before using Agent Creator"));}
    private String unique(String tenant,String base){String id=base;int suffix=2;while(Boolean.TRUE.equals(jdbc.sql("select exists(select 1 from agents where tenant_id=? and id=?)").params(tenant,id).query(Boolean.class).single()))id=base+"-"+suffix++;return id;}
    private static String name(String request){Matcher matcher=NAMED.matcher(request);if(matcher.find())return title(matcher.group(1).replaceFirst("(?i)^(called|named)\\s+","").replaceAll("(?i)\\s+(that|which|to|for|with)\\b.*$","").trim());String cleaned=request.replaceAll("(?i)^(please\\s+)?(create|build|make)\\s+(an?\\s+)?","").replaceAll("[.!?].*$","").trim();return title(cleaned.length()>48?cleaned.substring(0,48):cleaned);}
    private static String slug(String value){String slug=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-").replaceAll("(^-|-$)","");return slug.length()<3?"generated-agent":slug.substring(0,Math.min(64,slug.length()));}
    private static String title(String value){StringBuilder out=new StringBuilder();for(String word:value.split("\\s+")){if(word.isBlank())continue;if(!out.isEmpty())out.append(' ');out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));}return out.isEmpty()?"Generated Agent":out.toString();}
    private static Set<String> tools(String text){Set<String> result=new LinkedHashSet<>();if(contains(text,"postgres","postgresql")){result.add("postgres.describe-schema");result.add("postgres.query-readonly");}if(contains(text,"oracle")){result.add("oracle.describe-schema");result.add("oracle.query-readonly");}if(contains(text,"mysql")){result.add("mysql.describe-schema");result.add("mysql.query-readonly");}if(contains(text,"sql server","sqlserver")){result.add("sqlserver.describe-schema");result.add("sqlserver.query-readonly");}if(contains(text,"mongo","mongodb")){result.add("mongodb.describe-collections");result.add("mongodb.find-readonly");}if(contains(text,"redis")){result.add("redis.get");result.add("redis.search");}if(contains(text,"database","sql","jdbc")&&result.isEmpty()){result.add("database.describe-schema");result.add("database.query-readonly");}if(contains(text,"website","web page","url","http")){result.add("web.fetch");result.add("web.extract");}return Set.copyOf(result);}
    private static List<String> skills(String text){List<String> result=new ArrayList<>();if(contains(text,"database","sql","postgres","oracle","mysql")){result.add("github://ravikumar10/agent-studio-sample-registry/skills/text-to-sql/SKILL.md");result.add("github://ravikumar10/agent-studio-sample-registry/skills/sql-safety/SKILL.md");result.add("github://ravikumar10/agent-studio-sample-registry/skills/database-analysis/SKILL.md");}if(contains(text,"website","research","url"))result.add("github://ravikumar10/agent-studio-sample-registry/skills/website-research/SKILL.md");if(contains(text,"redis","memory"))result.add("catalog://skills/redis-memory");return List.copyOf(new LinkedHashSet<>(result));}
    private static boolean contains(String text,String... terms){String lower=text.toLowerCase(Locale.ROOT);return Arrays.stream(terms).anyMatch(lower::contains);}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public record CreatedAgent(String id,String displayName,String modelProfile,Set<String> toolCapabilities,List<String> skills,String answer){}
}
