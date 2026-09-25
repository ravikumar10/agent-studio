package dev.agentstudio.sample;

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
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private static final Pattern TAG=Pattern.compile("<[^>]+>");
    public WebReaderToolController(@Value("${sample.allowed-hosts}") String hosts){allowedHosts=Set.of(hosts.toLowerCase(Locale.ROOT).split(","));}

    @PostMapping("/tools/web.fetch")
    Map<String,Object> fetch(@RequestBody Map<String,Object> input)throws Exception{
        URI uri=URI.create(String.valueOf(input.get("url"))); String host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT);
        if(!"https".equals(uri.getScheme())||!allowedHosts.contains(host))throw new IllegalArgumentException("URL host is not allow-listed");
        for(InetAddress address:InetAddress.getAllByName(host))if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress())throw new IllegalArgumentException("private network targets are blocked");
        HttpResponse<String> response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("User-Agent","AgentStudioSample/1.0").GET().build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()/100!=2)throw new IllegalArgumentException("website returned HTTP "+response.statusCode());
        String text=TAG.matcher(response.body()).replaceAll(" ").replaceAll("\\s+"," ").trim();
        if(text.length()>4000)text=text.substring(0,4000);
        return Map.of("source",uri.toString(),"status",response.statusCode(),"text",text);
    }
}
