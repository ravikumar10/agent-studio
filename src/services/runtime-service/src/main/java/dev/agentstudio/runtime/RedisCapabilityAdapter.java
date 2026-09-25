package dev.agentstudio.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class RedisCapabilityAdapter {
    private static final Pattern KEY_IN_TEXT=Pattern.compile("(?i)(?:key|memory)\\s+(?:named\\s+)?[\"']?([a-zA-Z0-9._:-]{1,128})");
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    RedisCapabilityAdapter(StringRedisTemplate redis,ObjectMapper json){this.redis=redis;this.json=json;}

    Object invoke(String tenant,String capability,Map<String,Object> input){
        return switch(capability){case "redis.get"->get(tenant,input);case "redis.search"->search(tenant,input);default->throw new IllegalArgumentException("Unsupported Redis capability "+capability);};
    }

    private Map<String,Object> get(String tenant,Map<String,Object> input){
        String namespace=value(input,"namespace","agent-session");
        String key=value(input,"key",value(input,"memoryKey",inferKey(input)));
        if(key.isBlank())return Map.of("found",false,"requires",List.of("namespace","key"),"message","Provide a memory key in chat, for example: get key demo from namespace agent-session");
        String encoded=redis.opsForValue().get(redisKey(tenant,namespace,key));
        Map<String,Object> result=new LinkedHashMap<>();result.put("namespace",namespace);result.put("key",key);result.put("found",encoded!=null);result.put("content",encoded==null?Map.of():decode(encoded));return Map.copyOf(result);
    }

    private Map<String,Object> search(String tenant,Map<String,Object> input){
        String namespace=value(input,"namespace","*");String query=value(input,"query",value(input,"message",value(input,"question",""))).toLowerCase();
        int limit=Math.max(1,Math.min(number(input.get("limit"),20),50));String prefix="memory:"+safe(tenant)+":"+(namespace.equals("*")?"*":safe(namespace))+":";
        List<Map<String,Object>> matches=new ArrayList<>();
        redis.execute((RedisCallback<Object>)connection->{try(Cursor<byte[]> cursor=connection.keyCommands().scan(ScanOptions.scanOptions().match(prefix+"*").count(Math.min(limit*4L,200L)).build())){while(cursor.hasNext()&&matches.size()<limit){byte[] rawKey=cursor.next();String redisKey=new String(rawKey,StandardCharsets.UTF_8);byte[] rawValue=connection.stringCommands().get(rawKey);if(rawValue==null)continue;String encoded=new String(rawValue,StandardCharsets.UTF_8);String logical=redisKey.substring(("memory:"+safe(tenant)+":").length());int separator=logical.indexOf(':');String foundNamespace=separator<0?"":logical.substring(0,separator);String key=separator<0?logical:logical.substring(separator+1);if(!query.isBlank()&&!key.toLowerCase().contains(query)&&!encoded.toLowerCase().contains(query))continue;matches.add(Map.of("namespace",foundNamespace,"key",key,"content",decode(encoded)));}}return null;});
        return Map.of("query",query,"namespace",namespace,"count",matches.size(),"matches",List.copyOf(matches),"strategy","BOUNDED_LEXICAL_SCAN");
    }

    private String inferKey(Map<String,Object> input){String text=value(input,"message",value(input,"question",""));Matcher matcher=KEY_IN_TEXT.matcher(text);return matcher.find()?matcher.group(1):"";}
    private String redisKey(String tenant,String namespace,String key){return "memory:"+safe(tenant)+":"+safe(namespace)+":"+safe(key);}
    private static String safe(String value){if(value==null||!value.matches("[a-zA-Z0-9._-]{1,128}"))throw new IllegalArgumentException("Redis namespace and key may contain only letters, numbers, dot, underscore and dash");return value;}
    private static String value(Map<String,Object> input,String key,String fallback){Object value=input.get(key);return value==null?fallback:String.valueOf(value).trim();}
    private static int number(Object value,int fallback){try{return value==null?fallback:Integer.parseInt(String.valueOf(value));}catch(NumberFormatException ignored){return fallback;}}
    private Object decode(String value){try{return json.readValue(value,Object.class);}catch(Exception ignored){return value;}}
}
