package dev.agentstudio.runtime;

import java.util.*;
import org.springframework.stereotype.Component;

/** Produces a bounded plan from attached logical capabilities; it never invents endpoints or tools. */
@Component
class AgentLoopPlanner {
    private final ModelGateway models;
    AgentLoopPlanner(ModelGateway models){this.models=models;}

    Plan plan(String tenant,String profile,Map<String,Object> input,Collection<String> attached){
        LinkedHashSet<String> available=new LinkedHashSet<>(attached);Optional<ModelGateway.PlanResult> generated=models.plan(tenant,profile,input,available);
        LinkedHashSet<String> selected=new LinkedHashSet<>();String strategy="DETERMINISTIC_INTENT";String reason="Selected attached capabilities from bounded intent rules";ModelGateway.ModelResult usage=null;
        if(generated.isPresent()){var plan=generated.get();usage=plan.model();if(plan.valid()){selected.addAll(plan.capabilities());strategy="LLM_BOUNDED";reason=plan.reason();}}
        if(selected.isEmpty())selected.addAll(fallback(input,available));
        String message=Objects.toString(input.getOrDefault("message",input.getOrDefault("question","")),"").toLowerCase(Locale.ROOT);
        if(selected.contains("web.extract")&&available.contains("web.fetch"))selected.add("web.fetch");
        if(selected.contains("browser.extract")&&available.contains("browser.navigate"))selected.add("browser.navigate");
        if(selected.contains("chart.generate"))selected.addAll(dataCapabilities(message,available));
        LinkedHashSet<String> ordered=new LinkedHashSet<>();if(available.contains("knowledge.search"))ordered.add("knowledge.search");ordered.addAll(selected);if(available.contains("knowledge.store"))ordered.add("knowledge.store");ordered.retainAll(available);
        return new Plan(List.copyOf(ordered),strategy,reason,usage);
    }

    private Collection<String> fallback(Map<String,Object> input,Set<String> available){
        String text=Objects.toString(input.getOrDefault("message",input.getOrDefault("question","")),"").toLowerCase(Locale.ROOT);LinkedHashSet<String> result=new LinkedHashSet<>();
        if(text.contains("http://")||text.contains("https://")||contains(text,"website","webpage","url")){add(result,available,"web.fetch","web.extract","browser.navigate","browser.extract","http.request");}
        if(contains(text,"database","sql","table","schema","record","product","inventory"))for(String value:available)if(value.contains("database")||value.contains("postgres")||value.contains("mysql")||value.contains("oracle")||value.contains("sqlserver")||value.contains("mongodb"))result.add(value);
        if(contains(text,"redis","cache","memory"))for(String value:available)if(value.startsWith("redis."))result.add(value);
        if(contains(text,"chart","graph","plot","visual"))add(result,available,"chart.generate");
        if(contains(text,"email","mail","send","deliver","share"))add(result,available,"email.draft","email.send");
        if(result.isEmpty())for(String value:available)if(!value.startsWith("knowledge.")&&!"chart.generate".equals(value))result.add(value);
        return result;
    }
    private Collection<String> dataCapabilities(String text,Set<String> available){LinkedHashSet<String> result=new LinkedHashSet<>();if(text.contains("http")||contains(text,"web","url"))add(result,available,"web.fetch","web.extract","browser.navigate","browser.extract","http.request");for(String value:available)if(value.endsWith(".query-readonly")||value.endsWith(".find-readonly"))result.add(value);return result;}
    private static void add(Set<String> target,Set<String> available,String... values){for(String value:values)if(available.contains(value))target.add(value);}
    private static boolean contains(String text,String... values){return Arrays.stream(values).anyMatch(text::contains);}
    record Plan(List<String> capabilities,String strategy,String reason,ModelGateway.ModelResult modelUsage){}
}
