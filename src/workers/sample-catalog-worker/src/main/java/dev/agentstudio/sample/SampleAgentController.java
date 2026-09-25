package dev.agentstudio.sample;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
@ConditionalOnProperty(name="sample.role",havingValue="agent",matchIfMissing=true)
public class SampleAgentController {
    private static final Pattern URL=Pattern.compile("https://[^\\s]+",Pattern.CASE_INSENSITIVE);
    private static final Set<String> PRODUCT_CATEGORIES=Set.of("electronics","outdoors","home");
    private final RestClient web; private final RestClient database;
    public SampleAgentController(RestClient.Builder builder,@Value("${sample.web-tool-url}")String webUrl,@Value("${sample.database-tool-url}")String databaseUrl){web=builder.baseUrl(webUrl).build();database=builder.baseUrl(databaseUrl).build();}

    @PostMapping(value="/internal/v1/agent-invocations",consumes=MediaType.APPLICATION_JSON_VALUE)
    Map<String,Object> invoke(@RequestBody JsonNode invocation){
        String id=invocation.path("agentId").asText(); JsonNode input=invocation.path("request").path("input");
        long delay=Math.min(15_000,Math.max(0,input.path("delayMs").asLong(0)));
        if(delay>0)try{Thread.sleep(delay);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("sample run interrupted",interrupted);}
        Map<String,Object> output=switch(id){
          case "website-reader"->website(input);
          case "database-reader"->database(input);
          default->throw new IllegalArgumentException("unsupported sample agent: "+id);
        };
        return Map.of("status","COMPLETED","output",output,"artifacts",List.of(),"events",List.of(Map.of("type","tool.completed","timestamp",Instant.now().toString(),"attributes",Map.of("agentId",id))),"usage",Map.of("inputTokens",0,"outputTokens",0,"modelCalls",0,"costMicros",0));
    }
    private String message(JsonNode input){return input.path("message").asText(input.path("question").asText(""));}
    @SuppressWarnings("unchecked") private Map<String,Object> website(JsonNode input){
        String request=message(input); String url=input.path("url").asText("");
        if(url.isBlank()){Matcher match=URL.matcher(request);if(match.find())url=match.group().replaceAll("[),.;!?]+$","");}
        if(url.isBlank())throw new IllegalArgumentException("Include an https:// website URL in your chat message.");
        Map<String,Object> fetched=web.post().uri("/tools/web.fetch").body(Map.of("url",url,"question",request)).retrieve().body(Map.class);
        return Map.of("answer","Fetched approved webpage content for grounded analysis.","source",fetched.get("source"),"content",fetched.get("text"));
    }
    @SuppressWarnings("unchecked") private Map<String,Object> database(JsonNode input){
        String request=message(input).toLowerCase(Locale.ROOT); String category=input.path("category").asText("");
        if(category.isBlank())category=PRODUCT_CATEGORIES.stream().filter(request::contains).findFirst().orElse("");
        Map<String,Object> result=database.post().uri("/tools/database.query-readonly").body(Map.of("category",category)).retrieve().body(Map.class);
        return Map.of("answer","Returned products through an approved parameterized read-only query.","evidence",result);
    }
}
