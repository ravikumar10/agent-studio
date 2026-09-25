package dev.agentstudio.slack;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.util.LinkedMultiValueMap;

@RestController
@RequestMapping("/tools")
class SlackToolController {
    private final RestClient slack;private final RestClient uploads;private final ChartPngRenderer charts;
    SlackToolController(RestClient.Builder clients,@Value("${slack.api-base-url}")String baseUrl,ChartPngRenderer charts){
        this.slack=clients.clone().baseUrl(baseUrl).build();this.uploads=clients.clone().build();this.charts=charts;
    }

    @PostMapping("/slack.messages.send")
    Map<String,Object> send(@RequestHeader("X-Integration-Credential-botToken")String token,@RequestBody Map<String,Object> input){
        Map<String,Object> integration=map(input.get("_integration"));
        String channel=required(first(input.get("channel"),integration.get("defaultChannel")),"channel or configured defaultChannel");
        String text=message(input);
        List<Map<String,Object>> blocks=responseBlocks(text,List.of());
        Map<String,Object> response=post("chat.postMessage",token,Map.of("channel",channel,"text",plainFallback(text),"blocks",blocks));
        String timestamp=String.valueOf(response.getOrDefault("ts",""));
        List<Map<String,Object>> uploaded=uploadCharts(token,input,channel,timestamp);
        return Map.of("sent",true,"channel",String.valueOf(response.getOrDefault("channel",channel)),"timestamp",String.valueOf(response.getOrDefault("ts","")),"message",text,"uploadedCharts",uploaded,"uploadedChartCount",uploaded.size());
    }

    @PostMapping("/slack.verify")
    Map<String,Object> verify(@RequestHeader("X-Integration-Credential-botToken")String token){
        Map<String,Object> response=post("auth.test",token,Map.of());
        return Map.of(
                "valid",true,
                "workspace",String.valueOf(response.getOrDefault("team","")),
                "workspaceId",String.valueOf(response.getOrDefault("team_id","")),
                "botUser",String.valueOf(response.getOrDefault("user","")),
                "botUserId",String.valueOf(response.getOrDefault("user_id","")));
    }

    @PostMapping("/slack.messages.read")
    Map<String,Object> read(@RequestHeader("X-Integration-Credential-botToken")String token,@RequestBody Map<String,Object> input){
        Map<String,Object> integration=map(input.get("_integration"));
        String channel=required(first(input.get("channel"),integration.get("defaultChannel")),"channel or configured defaultChannel");
        int limit=Math.max(1,Math.min(100,integer(input.get("limit"),20)));
        Map<String,Object> response=post("conversations.history",token,Map.of("channel",channel,"limit",limit));
        return Map.of("channel",channel,"messages",response.getOrDefault("messages",List.of()),"hasMore",response.getOrDefault("has_more",false));
    }

    private Map<String,Object> post(String operation,String token,Map<String,Object> body){
        Map<String,Object> result=slack.post().uri("/"+operation).header(HttpHeaders.AUTHORIZATION,"Bearer "+required(token,"bot token")).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
        if(result==null||!Boolean.TRUE.equals(result.get("ok")))throw new SlackCallException(result==null?"empty_response":String.valueOf(result));
        return result;
    }
    private List<Map<String,Object>> uploadCharts(String token,Map<String,Object> input,String channel,String threadTimestamp){
        Map<String,Object> generated=map(input.get("chart.generate"));Object raw=generated.get("charts");if(!(raw instanceof Collection<?> values))return List.of();List<Map<String,Object>> result=new ArrayList<>();int index=0;
        for(Object value:values){if(!(value instanceof Map<?,?> rawChart))continue;Map<String,Object> chart=map(rawChart);index++;byte[] png=charts.render(chart);String title=String.valueOf(chart.getOrDefault("title","Chart "+index));String filename="agent-studio-chart-"+index+".png";Map<String,Object> slot=uploadSlot(token,filename,png.length);String fileId=required(slot.get("file_id"),"Slack upload file_id");String uploadUrl=required(slot.get("upload_url"),"Slack upload URL");
            uploads.post().uri(uploadUrl).contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(png.length).body(png).retrieve().toBodilessEntity();
            Map<String,Object> completed=post("files.completeUploadExternal",token,Map.of("files",List.of(Map.of("id",fileId,"title",title)),"channel_id",channel,"thread_ts",threadTimestamp));result.add(Map.of("fileId",fileId,"title",title,"filename",filename,"completed",Boolean.TRUE.equals(completed.get("ok"))));}
        return List.copyOf(result);
    }
    private List<Map<String,Object>> responseBlocks(String markdown,List<Map<String,Object>> uploaded){
        List<Map<String,Object>> blocks=new ArrayList<>();for(String chunk:slackMarkdownChunks(markdown))blocks.add(Map.of("type","section","text",Map.of("type","mrkdwn","text",chunk)));
        for(Map<String,Object> file:uploaded){String title=String.valueOf(file.get("title"));blocks.add(Map.of("type","image","slack_file",Map.of("id",file.get("fileId")),"alt_text",title,"title",Map.of("type","plain_text","text",title.substring(0,Math.min(title.length(),200)),"emoji",true)));}
        return List.copyOf(blocks);
    }
    private static List<String> slackMarkdownChunks(String markdown){
        String converted=markdown.replaceAll("(?m)^#{1,6}\\s+(.+)$","*$1*").replace("**","*");List<String> chunks=new ArrayList<>();int cursor=0;
        while(cursor<converted.length()&&chunks.size()<45){int end=Math.min(cursor+2_900,converted.length());if(end<converted.length()){int newline=converted.lastIndexOf('\n',end);if(newline>cursor+500)end=newline;}String chunk=converted.substring(cursor,end).trim();if(!chunk.isBlank())chunks.add(chunk);cursor=end;while(cursor<converted.length()&&converted.charAt(cursor)=='\n')cursor++;}
        return chunks.isEmpty()?List.of("Agent Studio task completed."):List.copyOf(chunks);
    }
    private static String plainFallback(String markdown){String clean=markdown.replaceAll("(?m)^#{1,6}\\s+","").replace("**","").replace("---","");return clean.substring(0,Math.min(clean.length(),4_000));}
    private Map<String,Object> uploadSlot(String token,String filename,int length){LinkedMultiValueMap<String,String> form=new LinkedMultiValueMap<>();form.add("filename",filename);form.add("length",String.valueOf(length));Map<String,Object> result=slack.post().uri("/files.getUploadURLExternal").header(HttpHeaders.AUTHORIZATION,"Bearer "+required(token,"bot token")).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);if(result==null||!Boolean.TRUE.equals(result.get("ok")))throw new SlackCallException(String.valueOf(result==null?"empty_response":result.getOrDefault("error","unknown_error")));return result;}
    private static String message(Map<String,Object> input){Object direct=first(input.get("report"),first(input.get("finalResponse"),first(input.get("text"),input.get("message"))));if(direct!=null&&!String.valueOf(direct).isBlank())return bounded(String.valueOf(direct));Object previous=input.get("previousResult");if(previous!=null)return bounded("Agent Studio report\n```\n"+String.valueOf(previous)+"\n```");return bounded(String.valueOf(input.getOrDefault("prompt","Agent Studio task completed.")));}
    private static String bounded(String value){String clean=value.trim();return clean.length()<=35000?clean:clean.substring(0,34980)+"\n… truncated";}
    private static Object first(Object first,Object second){return first==null||String.valueOf(first).isBlank()?second:first;}
    private static String required(Object value,String name){if(value==null||String.valueOf(value).isBlank())throw new IllegalArgumentException(name+" is required");return String.valueOf(value).trim();}
    private static int integer(Object value,int fallback){try{return value==null?fallback:Integer.parseInt(String.valueOf(value));}catch(NumberFormatException ignored){return fallback;}}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return value instanceof Map<?,?> map?(Map<String,Object>)map:Map.of();}
}

@ResponseStatus(HttpStatus.BAD_GATEWAY)
class SlackCallException extends RuntimeException {SlackCallException(String message){super("Slack API rejected the request: "+message);}}
