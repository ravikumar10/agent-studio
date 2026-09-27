package dev.agentstudio.runtime;

import java.util.*;
import org.springframework.stereotype.Component;

/** Produces a bounded plan from attached logical capabilities; it never invents endpoints or tools. */
@Component
class AgentLoopPlanner {
    private final ModelGateway models; private final SkillContextResolver skills;
    AgentLoopPlanner(ModelGateway models,SkillContextResolver skills){this.models=models;this.skills=skills;}

    Plan plan(String tenant,String profile,String promptRef,Map<String,Object> input,Collection<String> attached){
        LinkedHashSet<String> available=new LinkedHashSet<>(attached);Optional<ModelGateway.PlanResult> generated=models.plan(tenant,profile,input,available,skills.resolve(tenant,promptRef));
        LinkedHashSet<String> selected=new LinkedHashSet<>();Map<String,Map<String,Object>> arguments=new LinkedHashMap<>();String strategy="DETERMINISTIC_INTENT";String reason="Selected attached capabilities from bounded intent rules";ModelGateway.ModelResult usage=null;
        if(generated.isPresent()){var plan=generated.get();usage=plan.model();if(plan.valid()){selected.addAll(plan.capabilities());arguments.putAll(plan.arguments());strategy="LLM_BOUNDED_SKILL_GUIDED";reason=plan.reason();}}
        if(selected.isEmpty())selected.addAll(fallback(input,available));
        String message=Objects.toString(input.getOrDefault("message",input.getOrDefault("question","")),"").toLowerCase(Locale.ROOT);
        // Explicit user intent is mandatory when the matching governed capability is attached.
        // The model may refine arguments, but it cannot silently omit requested delivery or artifacts.
        selected.addAll(explicitRequirements(message,available));
        if(selected.contains("web.extract")&&available.contains("web.fetch"))selected.add("web.fetch");
        if(selected.contains("browser.extract")&&available.contains("browser.navigate"))selected.add("browser.navigate");
        if(selected.contains("chart.generate"))selected.addAll(dataCapabilities(message,available));
        if(selected.contains("slack.messages.send")&&contains(message,"report","summary","analysis","dashboard"))selected.addAll(dataCapabilities(message,available));
        LinkedHashSet<String> ordered=new LinkedHashSet<>();if(available.contains("knowledge.search"))ordered.add("knowledge.search");ordered.addAll(selected);if(available.contains("knowledge.store"))ordered.add("knowledge.store");ordered.retainAll(available);
        arguments.keySet().retainAll(ordered);return new Plan(List.copyOf(ordered),Map.copyOf(arguments),strategy,reason,usage);
    }

    private Collection<String> fallback(Map<String,Object> input,Set<String> available){
        String text=Objects.toString(input.getOrDefault("message",input.getOrDefault("question","")),"").toLowerCase(Locale.ROOT);LinkedHashSet<String> result=new LinkedHashSet<>();
        if(text.contains("http://")||text.contains("https://")||contains(text,"website","webpage","url")){add(result,available,"web.fetch","web.extract","browser.navigate","browser.extract","http.request");}
        if(contains(text,"database","sql","table","schema","record","product","inventory"))for(String value:available)if(value.contains("database")||value.contains("postgres")||value.contains("mysql")||value.contains("oracle")||value.contains("sqlserver")||value.contains("mongodb"))result.add(value);
        if(contains(text,"redis","cache","memory"))for(String value:available)if(value.startsWith("redis."))result.add(value);
        if(contains(text,"chart","graph","plot","visual"))add(result,available,"chart.generate");
        if(contains(text,"slack"))add(result,available,"slack.messages.send");
        if(contains(text,"email","mail","send","deliver","share"))add(result,available,"email.draft","email.send");
        if(result.isEmpty())for(String value:available)if(!value.startsWith("knowledge.")&&!"chart.generate".equals(value))result.add(value);
        return result;
    }
    private Collection<String> dataCapabilities(String text,Set<String> available){LinkedHashSet<String> result=new LinkedHashSet<>();if(text.contains("http")||contains(text,"web","url"))add(result,available,"web.fetch","web.extract","browser.navigate","browser.extract","http.request");for(String value:available)if(value.endsWith(".query-readonly")||value.endsWith(".find-readonly"))result.add(value);return result;}
    private Collection<String> explicitRequirements(String text,Set<String> available){LinkedHashSet<String> result=new LinkedHashSet<>();if(contains(text,"chart","graph","plot","visual"))add(result,available,"chart.generate");if(text.contains("slack")&&contains(text,"send","post","share","deliver","notify","report","message"))add(result,available,"slack.messages.send");if(contains(text,"email","mail")&&contains(text,"send","share","deliver"))add(result,available,"email.send");return result;}
    private static void add(Set<String> target,Set<String> available,String... values){for(String value:values)if(available.contains(value))target.add(value);}
    private static boolean contains(String text,String... values){return Arrays.stream(values).anyMatch(text::contains);}
    record Plan(List<String> capabilities,Map<String,Map<String,Object>> arguments,String strategy,String reason,ModelGateway.ModelResult modelUsage){}
}
