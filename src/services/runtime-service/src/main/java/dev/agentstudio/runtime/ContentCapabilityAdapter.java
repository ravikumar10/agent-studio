package dev.agentstudio.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class ContentCapabilityAdapter {
    private static final Pattern WORD=Pattern.compile("[^a-z0-9]+");
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    ContentCapabilityAdapter(StringRedisTemplate redis,ObjectMapper json){this.redis=redis;this.json=json;}

    Object invoke(String tenant,String agentId,String capability,Map<String,Object> input){return switch(capability){
        case "knowledge.store"->store(tenant,agentId,input);
        case "knowledge.search"->search(tenant,agentId,input);
        case "chart.generate"->chart(input);
        default->throw new IllegalArgumentException("Unsupported content capability "+capability);
    };}

    private Map<String,Object> store(String tenant,String agentId,Map<String,Object> input){
        Object previous=input.get("previousResult");
        Object source=input.get("content");
        if(source==null)source=input.get("web.fetch");
        if(source==null)source=input.get("browser.extract");
        if(source==null)source=input.entrySet().stream().filter(entry->entry.getKey().endsWith(".query-readonly")||entry.getKey().endsWith(".find-readonly")).map(Map.Entry::getValue).findFirst().orElse(null);
        boolean previousIsDerived=previous instanceof Map<?,?> map&&(map.containsKey("strategy")||map.containsKey("generated"));
        if(source==null&&previous!=null&&!previousIsDerived)source=previous;
        if(source==null)source=input.getOrDefault("message","");
        String text=source instanceof String value?value:write(source);if(text.isBlank())return Map.of("stored",false,"reason","no content");
        String id=UUID.randomUUID().toString();Map<String,Object> record=Map.of("id",id,"text",text.substring(0,Math.min(text.length(),20_000)),"vector",tokens(text),"storedAt",Instant.now().toString());
        redis.opsForValue().set(key(tenant,agentId,id),write(record),Duration.ofDays(30));
        return Map.of("stored",true,"id",id,"namespace",agentId,"dimensions",((Collection<?>)record.get("vector")).size(),"ttlDays",30);
    }
    private Map<String,Object> search(String tenant,String agentId,Map<String,Object> input){
        String query=String.valueOf(input.getOrDefault("message",input.getOrDefault("question","")));Set<String> queryVector=tokens(query);List<Map<String,Object>> collected=new ArrayList<>();
        redis.execute((RedisCallback<Object>)connection->{try(Cursor<byte[]> cursor=connection.keyCommands().scan(ScanOptions.scanOptions().match(prefix(tenant,agentId)+"*").count(100).build())){while(cursor.hasNext()){byte[] raw=cursor.next();byte[] value=connection.stringCommands().get(raw);if(value==null)continue;Map<String,Object> record=read(new String(value,StandardCharsets.UTF_8));double score=similarity(queryVector,new LinkedHashSet<>((Collection<String>)record.getOrDefault("vector",List.of())));if(score>0)collected.add(Map.of("id",record.get("id"),"score",score,"content",record.get("text")));}}return null;});
        collected.sort((left,right)->Double.compare((double)right.get("score"),(double)left.get("score")));List<Map<String,Object>> matches=collected.size()>5?collected.subList(0,5):collected;
        return Map.of("query",query,"count",matches.size(),"matches",List.copyOf(matches),"strategy","REDIS_SPARSE_VECTOR_COSINE","namespace",agentId);
    }
    private Map<String,Object> chart(Map<String,Object> input){
        List<Map<String,Object>> rows=findRows(input.getOrDefault("previousResult",input));if(rows.isEmpty())return Map.of("generated",false,"reason","No tabular MCP data was available");
        List<Map<String,Object>> bounded=rows.subList(0,Math.min(rows.size(),250));LinkedHashSet<String> keys=new LinkedHashSet<>();bounded.forEach(row->keys.addAll(row.keySet()));
        List<String> numbers=keys.stream().filter(key->!key.equalsIgnoreCase("id")&&!key.toLowerCase(Locale.ROOT).endsWith("_id")).filter(key->bounded.stream().anyMatch(row->row.get(key) instanceof Number)).limit(8).toList();if(numbers.isEmpty())return Map.of("generated",false,"reason","No numeric field was available");
        String category=List.of("label","name","category","source","date","timestamp").stream().filter(keys::contains).findFirst().orElseGet(()->keys.stream().filter(key->!numbers.contains(key)&&bounded.stream().anyMatch(row->{Object value=row.get(key);return value!=null&&String.valueOf(value).length()<160;})).findFirst().orElse(numbers.get(0)));
        String request=String.valueOf(input.getOrDefault("message",input.getOrDefault("question",""))).toLowerCase(Locale.ROOT);LinkedHashSet<String> types=new LinkedHashSet<>();
        if(request.contains("bar"))types.add(numbers.size()>1?"grouped-bar":"bar");if(request.contains("line")||request.contains("trend"))types.add("line");if((request.contains("scatter")||request.contains("correlation"))&&numbers.size()>1)types.add("scatter");if(request.contains("pie"))types.add("pie");if(request.contains("donut"))types.add("donut");
        boolean multiple=request.contains("multiple chart")||request.contains("several chart")||request.contains("dashboard")||request.contains("all chart")||request.contains("charts");
        if(types.isEmpty())types.add(numbers.size()>1?"grouped-bar":"bar");if(multiple){types.add("line");if(numbers.size()>1)types.add("scatter");types.add("donut");}
        List<Map<String,Object>> charts=types.stream().limit(4).map(type->{Map<String,Object> spec=chartSpec(bounded,keys,numbers,category,type);return Map.<String,Object>of("id","chart-"+(types.stream().toList().indexOf(type)+1),"title",spec.get("title"),"chartType",type,"format","VEGA_LITE","spec",spec);}).toList();Map<String,Object> first=charts.get(0);
        return Map.of("generated",true,"format","VEGA_LITE","chartType",first.get("chartType"),"spec",first.get("spec"),"charts",charts,"chartCount",charts.size(),"rowCount",bounded.size(),"downloadFormats",List.of("PNG","SVG","CSV","VEGA_LITE_JSON"));
    }
    private Map<String,Object> chartSpec(List<Map<String,Object>> rows,Set<String> keys,List<String> numbers,String category,String chartType){Map<String,Object> encoding=new LinkedHashMap<>();List<Map<String,Object>> transforms=new ArrayList<>();Object mark;
        if("scatter".equals(chartType)){mark=Map.of("type","point","filled",true,"size",90,"opacity",0.75);encoding.put("x",field(numbers.get(0),"quantitative"));encoding.put("y",field(numbers.get(1),"quantitative"));encoding.put("color",field(category,"nominal"));}
        else if("pie".equals(chartType)||"donut".equals(chartType)){mark=Map.of("type","arc","innerRadius","donut".equals(chartType)?70:0,"tooltip",true);encoding.put("theta",field(numbers.get(0),"quantitative"));encoding.put("color",field(category,"nominal"));}
        else if("line".equals(chartType)){mark=Map.of("type","line","point",true,"tooltip",true);encoding.put("x",field(category,category.toLowerCase(Locale.ROOT).matches(".*(date|time).*" )?"temporal":"ordinal"));encoding.put("y",field(numbers.get(0),"quantitative"));if(numbers.size()>1){transforms.add(Map.of("fold",numbers,"as",List.of("metric","value")));encoding.put("y",field("value","quantitative"));encoding.put("color",field("metric","nominal"));}}
        else if("grouped-bar".equals(chartType)){mark=Map.of("type","bar","tooltip",true);transforms.add(Map.of("fold",numbers,"as",List.of("metric","value")));encoding.put("x",field(category,"nominal"));encoding.put("xOffset",field("metric","nominal"));encoding.put("y",field("value","quantitative"));encoding.put("color",field("metric","nominal"));}
        else{mark=Map.of("type","bar","tooltip",true,"cornerRadiusEnd",3);encoding.put("x",field(category,"nominal"));encoding.put("y",field(numbers.get(0),"quantitative"));encoding.put("color",Map.of("value","#39795c"));}
        encoding.put("tooltip",keys.stream().limit(12).map(key->field(key,numbers.contains(key)?"quantitative":"nominal")).toList());Map<String,Object> spec=new LinkedHashMap<>();spec.put("$schema","https://vega.github.io/schema/vega-lite/v5.json");spec.put("title",title(chartType,numbers,category));spec.put("width","container");spec.put("height",360);spec.put("data",Map.of("values",rows));if(!transforms.isEmpty())spec.put("transform",transforms);spec.put("mark",mark);spec.put("encoding",encoding);spec.put("config",Map.of("view",Collections.singletonMap("stroke",null),"axis",Map.of("labelLimit",180,"gridColor","#e5ebe6"),"legend",Map.of("orient","bottom")));return spec;}
    private String title(String chartType,List<String> numbers,String category){return switch(chartType){case "scatter"->numbers.get(0)+" vs "+numbers.get(1);case "pie","donut"->numbers.get(0)+" share by "+category;case "line"->String.join(", ",numbers)+" trend by "+category;default->String.join(", ",numbers)+" by "+category;};}
    private Map<String,Object> field(String name,String type){return Map.of("field",name,"type",type,"title",name.replace('_',' '));}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> findRows(Object value){if(value instanceof Collection<?> values){List<Map<String,Object>> rows=new ArrayList<>();for(Object item:values)if(item instanceof Map<?,?> map)rows.add((Map<String,Object>)map);if(!rows.isEmpty())return rows;}if(value instanceof Map<?,?> map)for(Object nested:map.values()){List<Map<String,Object>> rows=findRows(nested);if(!rows.isEmpty())return rows;}return List.of();}
    private Set<String> tokens(String text){Set<String> result=new LinkedHashSet<>();for(String token:WORD.split(text.toLowerCase(Locale.ROOT)))if(token.length()>2)result.add(token);return result;}
    private double similarity(Set<String> left,Set<String> right){if(left.isEmpty()||right.isEmpty())return 0;int common=0;for(String value:left)if(right.contains(value))common++;return Math.round((common/Math.sqrt((double)left.size()*right.size()))*10_000d)/10_000d;}
    private String prefix(String tenant,String agent){return "knowledge:"+safe(tenant)+":"+safe(agent)+":";}private String key(String tenant,String agent,String id){return prefix(tenant,agent)+id;}
    private static String safe(String value){return value.replaceAll("[^a-zA-Z0-9._-]","_");}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception error){throw new IllegalStateException(error);}}
    @SuppressWarnings("unchecked") private Map<String,Object> read(String value){try{return json.readValue(value,Map.class);}catch(Exception error){return Map.of();}}
}
