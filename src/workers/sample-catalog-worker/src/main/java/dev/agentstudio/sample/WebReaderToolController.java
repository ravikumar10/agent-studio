package dev.agentstudio.sample;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name="sample.role",havingValue="web-tool")
public class WebReaderToolController {
    private final Set<String> allowedHosts;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Pattern NON_CONTENT=Pattern.compile("<(script|style|noscript)[^>]*>.*?</\\1>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
    private static final Pattern TAG=Pattern.compile("<[^>]+>");
    private static final Pattern URL=Pattern.compile("https?://[^\\s<>]+",Pattern.CASE_INSENSITIVE);
    public WebReaderToolController(@Value("${sample.allowed-hosts}") String hosts,ObjectMapper json){allowedHosts=Set.of(hosts.toLowerCase(Locale.ROOT).split(","));this.json=json;}

    @PostMapping("/tools/web.fetch")
    Map<String,Object> fetch(@RequestBody Map<String,Object> input)throws Exception{
        List<String> urls=urls(input);if(urls.isEmpty())throw new IllegalArgumentException("Include one or more HTTP(S) URLs in the chat message");
        List<Map<String,Object>> pages=new ArrayList<>();for(String value:urls)pages.add(fetchOne(value));
        return pages.size()==1?pages.get(0):Map.of("pages",List.copyOf(pages),"count",pages.size());
    }

    private Map<String,Object> fetchOne(String value)throws Exception{
        URI uri=URI.create(value); String host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT);
        if(!Set.of("http","https").contains(uri.getScheme())||!hostEnabled(allowedHosts,host))throw new IllegalArgumentException("URL host is not enabled by this integration");
        for(InetAddress address:InetAddress.getAllByName(host))if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress())throw new IllegalArgumentException("private network targets are blocked");
        HttpResponse<String> response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("User-Agent","AgentStudioSample/1.0").GET().build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()/100!=2)throw new IllegalArgumentException("website returned HTTP "+response.statusCode());
        String text=TAG.matcher(NON_CONTENT.matcher(response.body()).replaceAll(" ")).replaceAll(" ").replaceAll("&nbsp;"," ").replaceAll("&amp;","&").replaceAll("&#39;","'").replaceAll("&quot;","\"").replaceAll("\\s+"," ").trim();
        if(text.length()>4000)text=text.substring(0,4000);
        int wordCount=text.isBlank()?0:text.split("\\s+").length;
        return Map.of("source",uri.toString(),"status",response.statusCode(),"text",text,"wordCount",wordCount,"characterCount",text.length());
    }

    private List<String> urls(Map<String,Object> input){
        LinkedHashSet<String> result=new LinkedHashSet<>();Object explicit=input.get("urls");
        if(explicit instanceof Collection<?> values)for(Object value:values)result.add(String.valueOf(value));
        if(input.get("url")!=null)result.add(String.valueOf(input.get("url")));
        String message=Objects.toString(input.getOrDefault("message",input.getOrDefault("question","")));var matcher=URL.matcher(message);while(matcher.find()&&result.size()<5)result.add(matcher.group().replaceAll("[),.;]+$",""));
        return result.stream().filter(value->!value.isBlank()).limit(5).toList();
    }

    @PostMapping("/tools/http.request")
    Map<String,Object> request(@RequestBody Map<String,Object> input)throws Exception{
        URI uri=URI.create(String.valueOf(input.get("url")));Map<?,?> configuration=input.get("_integration") instanceof Map<?,?> value?value:Map.of();
        Set<String> hosts=configuredSet(configuration.get("allowedHosts"),allowedHosts);String host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT);
        if(!Set.of("http","https").contains(uri.getScheme())||!hostEnabled(hosts,host))throw new IllegalArgumentException("HTTP target host is not enabled by this integration");
        for(InetAddress address:InetAddress.getAllByName(host))if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress())throw new IllegalArgumentException("private network targets are blocked");
        String method=Objects.toString(input.getOrDefault("method","GET")).toUpperCase(Locale.ROOT);Set<String> methods=new HashSet<>();configuredSet(configuration.get("allowedMethods"),Set.of("GET")).forEach(value->methods.add(value.toUpperCase(Locale.ROOT)));
        if(!methods.contains(method))throw new IllegalArgumentException("HTTP method is not enabled for this integration profile");
        Object configuredTimeout=configuration.containsKey("timeoutSeconds")?configuration.get("timeoutSeconds"):10;
        int timeout=Math.min(30,Math.max(1,Integer.parseInt(Objects.toString(configuredTimeout))));
        HttpRequest.Builder builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout)).header("User-Agent","AgentStudioMCP/1.0").header("Accept","application/json,text/plain,*/*");
        Object body=input.get("body");if(Set.of("GET","HEAD").contains(method))builder.method(method,HttpRequest.BodyPublishers.noBody());else builder.method(method,HttpRequest.BodyPublishers.ofString(body instanceof String text?text:json.writeValueAsString(body==null?Map.of():body))).header("Content-Type",Objects.toString(input.getOrDefault("contentType","application/json")));
        HttpResponse<String> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());String payload=response.body()==null?"":response.body();if(payload.length()>16_000)payload=payload.substring(0,16_000);
        return Map.of("source",uri.toString(),"method",method,"status",response.statusCode(),"contentType",response.headers().firstValue("content-type").orElse(""),"body",payload);
    }

    private static Set<String> configuredSet(Object value,Set<String> fallback){
        if(value==null)return fallback;
        Collection<?> values=value instanceof Collection<?> collection?collection:List.of(String.valueOf(value).split(","));
        Set<String> result=new LinkedHashSet<>();for(Object item:values){String normalized=String.valueOf(item).trim().toLowerCase(Locale.ROOT);if(!normalized.isBlank())result.add(normalized);}
        return result.isEmpty()?fallback:Set.copyOf(result);
    }
    static boolean hostEnabled(Set<String> configured,String host){return configured.contains("*")||configured.contains(host);}
}
