package dev.agentstudio.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/deployments")
public class DeploymentController {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public DeploymentController(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @GetMapping("/environments")
    List<EnvironmentView> environments(@RequestHeader("X-Tenant-Id") String tenant,
                                       @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user) {
        return jdbc.sql("select * from deployment_environments where tenant_id=? and owner_user_id=? order by environment_id")
                .params(required(tenant),required(user)).query((rs,n)->new EnvironmentView(
                        rs.getString("environment_id"),rs.getString("display_name"),rs.getString("target_type"),
                        rs.getString("cloud_provider"),rs.getString("namespace"),readMap(rs.getString("adapter_config")),
                        maskRef(rs.getString("credential_ref")),rs.getString("status"),rs.getTimestamp("updated_at").toInstant())).list();
    }

    @PostMapping("/environments") @ResponseStatus(HttpStatus.CREATED)
    EnvironmentView createEnvironment(@RequestHeader("X-Tenant-Id") String tenant,
                                      @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                                      @Valid @RequestBody EnvironmentInput input) {
        String tenantId=required(tenant); String userId=required(user); String id=slug(input.environmentId());
        String target=enumValue(input.targetType(),List.of("STUDIO","KUBERNETES"),"targetType");
        String provider=enumValue(input.cloudProvider(),List.of("LOCAL","AZURE","AWS","GCP","OPENSHIFT","ON_PREM"),"cloudProvider");
        if(target.equals("STUDIO")&&!provider.equals("LOCAL"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Studio targets must use LOCAL provider");
        if(provider.equals("AZURE")){requireConfig(input.adapterConfig(),"subscriptionId");requireConfig(input.adapterConfig(),"resourceGroup");requireConfig(input.adapterConfig(),"clusterName");}
        if(input.credentialRef()!=null&&!input.credentialRef().isBlank()&&!input.credentialRef().matches("^(k8secret|vault|azurekeyvault)://.+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"credentialRef must be a secret reference, never a raw credential");
        jdbc.sql("insert into deployment_environments(tenant_id,environment_id,owner_user_id,display_name,target_type,cloud_provider,namespace,adapter_config,credential_ref,status) values(?,?,?,?,?,?,?,?::jsonb,?,'READY')")
                .params(tenantId,id,userId,required(input.displayName()),target,provider,kubeName(input.namespace()),write(input.adapterConfig()),blankToNull(input.credentialRef())).update();
        return environments(tenantId,userId).stream().filter(e->e.environmentId().equals(id)).findFirst().orElseThrow();
    }

    @DeleteMapping("/environments/{environmentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteEnvironment(@RequestHeader("X-Tenant-Id") String tenant,
                           @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                           @PathVariable String environmentId) {
        Integer uses=jdbc.sql("select count(*) from deployment_plans where tenant_id=? and environment_id=?")
                .params(required(tenant),environmentId).query(Integer.class).single();
        if(uses>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Delete the environment's deployment plans first");
        int deleted=jdbc.sql("delete from deployment_environments where tenant_id=? and owner_user_id=? and environment_id=? and environment_id<>'studio-local'")
                .params(tenant,user,environmentId).update();
        if(deleted==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"environment not found or protected");
    }

    @GetMapping
    List<DeploymentView> deployments(@RequestHeader("X-Tenant-Id") String tenant,
                                     @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user) {
        return jdbc.sql("select * from deployment_plans where tenant_id=? and owner_user_id=? order by created_at desc")
                .params(required(tenant),required(user)).query((rs,n)->new DeploymentView(
                        rs.getString("deployment_id"),rs.getString("environment_id"),rs.getString("agent_id"),
                        rs.getString("agent_version"),rs.getString("deployment_mode"),rs.getString("desired_state"),
                        rs.getString("observed_state"),rs.getString("image_ref"),rs.getInt("replicas"),
                        rs.getString("manifest_yaml"),readMap(rs.getString("configuration")),rs.getString("last_error"),
                        rs.getTimestamp("updated_at").toInstant())).list();
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    DeploymentView plan(@RequestHeader("X-Tenant-Id") String tenant,
                        @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                        @Valid @RequestBody DeploymentInput input) {
        String tenantId=required(tenant); String userId=required(user);
        EnvironmentRecord environment=jdbc.sql("select * from deployment_environments where tenant_id=? and owner_user_id=? and environment_id=?")
                .params(tenantId,userId,input.environmentId()).query((rs,n)->new EnvironmentRecord(
                        rs.getString("environment_id"),rs.getString("target_type"),rs.getString("cloud_provider"),rs.getString("namespace"),
                        readMap(rs.getString("adapter_config")),rs.getString("credential_ref"))).optional()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"deployment environment not found"));
        String version=input.agentVersion();
        if(version==null||version.isBlank())version=jdbc.sql("select active_version from agent_release_state where tenant_id=? and agent_id=?")
                .params(tenantId,input.agentId()).query(String.class).optional()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"agent has no active version"));
        Integer exists=jdbc.sql("select count(*) from agent_versions where tenant_id=? and agent_id=? and version=?")
                .params(tenantId,input.agentId(),version).query(Integer.class).single();
        if(exists==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"agent version not found");
        String deploymentId=UUID.randomUUID().toString();
        String mode=environment.targetType();
        String image=mode.equals("STUDIO")?null:required(input.imageRef());
        int replicas=input.replicas()==null?1:input.replicas();
        String manifest=mode.equals("STUDIO")?studioPlan(input.agentId(),version):kubernetesManifest(environment,input.agentId(),version,image,replicas,input.cronExpression(),input.timeZone());
        Map<String,Object> config=Map.of("brainProfile",blankDefault(input.brainProfile(),"default-brain"),"healthGate",true,"autoRollback",true,"generatedBy","deployment-brain");
        jdbc.sql("insert into deployment_plans(tenant_id,deployment_id,owner_user_id,environment_id,agent_id,agent_version,deployment_mode,desired_state,observed_state,image_ref,replicas,manifest_yaml,configuration) values(?,?,?,?,?,?,?,'RUNNING','PLANNED',?,?,?,?::jsonb)")
                .params(tenantId,deploymentId,userId,environment.environmentId(),input.agentId(),version,mode,image,replicas,manifest,write(config)).update();
        if(input.cronExpression()!=null&&!input.cronExpression().isBlank())createSchedule(tenantId,userId,new ScheduleInput(
                kubeName(input.agentId())+"-schedule",deploymentId,input.cronExpression(),blankDefault(input.timeZone(),"UTC"),true,input.scheduleInput()==null?Map.of():input.scheduleInput()));
        return deployment(tenantId,userId,deploymentId);
    }

    @PostMapping("/{deploymentId}/apply")
    DeploymentView apply(@RequestHeader("X-Tenant-Id") String tenant,
                         @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                         @PathVariable String deploymentId) {
        DeploymentView current=deployment(required(tenant),required(user),deploymentId);
        String state;
        if(current.deploymentMode().equals("STUDIO"))state="RUNNING";
        else {
            String credential=jdbc.sql("select e.credential_ref from deployment_environments e join deployment_plans d on d.tenant_id=e.tenant_id and d.environment_id=e.environment_id where d.tenant_id=? and d.deployment_id=?")
                    .params(tenant,deploymentId).query(String.class).optional().orElse(null);
            if(credential==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Configure a Kubernetes credential reference before applying this plan");
            state="APPLY_REQUESTED";
        }
        jdbc.sql("update deployment_plans set observed_state=?,updated_at=now(),last_error=null where tenant_id=? and deployment_id=? and owner_user_id=?")
                .params(state,tenant,deploymentId,user).update();
        return deployment(tenant,user,deploymentId);
    }

    @DeleteMapping("/{deploymentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteDeployment(@RequestHeader("X-Tenant-Id") String tenant,
                          @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                          @PathVariable String deploymentId) {
        int deleted=jdbc.sql("delete from deployment_plans where tenant_id=? and owner_user_id=? and deployment_id=?")
                .params(required(tenant),required(user),deploymentId).update();
        if(deleted==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"deployment not found");
    }

    @GetMapping("/schedules")
    List<ScheduleView> schedules(@RequestHeader("X-Tenant-Id") String tenant,
                                 @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user) {
        return jdbc.sql("select * from agent_schedules where tenant_id=? and owner_user_id=? order by schedule_id")
                .params(required(tenant),required(user)).query((rs,n)->new ScheduleView(rs.getString("schedule_id"),rs.getString("deployment_id"),
                        rs.getString("cron_expression"),rs.getString("time_zone"),rs.getBoolean("enabled"),readMap(rs.getString("input")))).list();
    }

    @PostMapping("/schedules") @ResponseStatus(HttpStatus.CREATED)
    ScheduleView schedule(@RequestHeader("X-Tenant-Id") String tenant,
                          @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                          @Valid @RequestBody ScheduleInput input) { return createSchedule(required(tenant),required(user),input); }

    @DeleteMapping("/schedules/{scheduleId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteSchedule(@RequestHeader("X-Tenant-Id") String tenant,
                        @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                        @PathVariable String scheduleId) {
        jdbc.sql("delete from agent_schedules where tenant_id=? and owner_user_id=? and schedule_id=?").params(required(tenant),required(user),scheduleId).update();
    }

    @GetMapping("/brain")
    List<BrainView> brains(@RequestHeader("X-Tenant-Id") String tenant,
                           @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user) {
        return jdbc.sql("select * from brain_profiles where tenant_id=? and owner_user_id=? order by profile_id")
                .params(required(tenant),required(user)).query((rs,n)->new BrainView(rs.getString("profile_id"),rs.getString("display_name"),
                        rs.getString("model_profile"),readMap(rs.getString("policy")),rs.getBoolean("enabled"))).list();
    }

    @PutMapping("/brain/{profileId}")
    BrainView saveBrain(@RequestHeader("X-Tenant-Id") String tenant,
                        @RequestHeader(value="X-User-Id",defaultValue="studio-user") String user,
                        @PathVariable String profileId,@Valid @RequestBody BrainInput input) {
        Map<String,Object> policy=input.policy()==null?Map.of():input.policy();
        jdbc.sql("insert into brain_profiles(tenant_id,profile_id,owner_user_id,display_name,model_profile,policy,enabled) values(?,?,?,?,?,?::jsonb,?) on conflict(tenant_id,profile_id) do update set display_name=excluded.display_name,model_profile=excluded.model_profile,policy=excluded.policy,enabled=excluded.enabled,updated_at=now()")
                .params(required(tenant),slug(profileId),required(user),required(input.displayName()),blankToNull(input.modelProfile()),write(policy),input.enabled()).update();
        return brains(tenant,user).stream().filter(b->b.profileId().equals(slug(profileId))).findFirst().orElseThrow();
    }

    private ScheduleView createSchedule(String tenant,String user,ScheduleInput input){
        validateCron(input.cronExpression());
        String id=slug(input.scheduleId());
        jdbc.sql("insert into agent_schedules(tenant_id,schedule_id,owner_user_id,deployment_id,cron_expression,time_zone,enabled,input) values(?,?,?,?,?,?,?,?::jsonb) on conflict(tenant_id,schedule_id) do update set cron_expression=excluded.cron_expression,time_zone=excluded.time_zone,enabled=excluded.enabled,input=excluded.input,updated_at=now()")
                .params(tenant,id,user,input.deploymentId(),input.cronExpression().trim(),blankDefault(input.timeZone(),"UTC"),input.enabled(),write(input.input()==null?Map.of():input.input())).update();
        return new ScheduleView(id,input.deploymentId(),input.cronExpression().trim(),blankDefault(input.timeZone(),"UTC"),input.enabled(),input.input()==null?Map.of():input.input());
    }

    private DeploymentView deployment(String tenant,String user,String id){
        return deployments(tenant,user).stream().filter(d->d.deploymentId().equals(id)).findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"deployment not found"));
    }

    private String kubernetesManifest(EnvironmentRecord e,String agent,String version,String image,int replicas,String cron,String zone){
        String name=kubeName(agent); String sa=name+"-agent"; String wi=stringValue(e.adapterConfig().get("workloadIdentityClientId"));
        String azureSa=e.cloudProvider().equals("AZURE")&&!wi.isBlank()?"\n  annotations:\n    azure.workload.identity/client-id: \""+yaml(wi)+"\"":"";
        String azurePod=e.cloudProvider().equals("AZURE")&&!wi.isBlank()?"\n        azure.workload.identity/use: \"true\"":"";
        String base="""
                apiVersion: v1
                kind: Namespace
                metadata:
                  name: %s
                ---
                apiVersion: v1
                kind: ServiceAccount
                metadata:
                  name: %s
                  namespace: %s%s
                ---
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                  namespace: %s
                  labels:
                    app.kubernetes.io/name: %s
                    agentstudio.io/agent-id: %s
                    agentstudio.io/agent-version: \"%s\"
                spec:
                  replicas: %d
                  strategy:
                    type: RollingUpdate
                  selector:
                    matchLabels:
                      app.kubernetes.io/name: %s
                  template:
                    metadata:
                      labels:
                        app.kubernetes.io/name: %s%s
                    spec:
                      serviceAccountName: %s
                      containers:
                        - name: agent
                          image: %s
                          imagePullPolicy: IfNotPresent
                          env:
                            - {name: AGENT_ID, value: \"%s\"}
                            - {name: AGENT_VERSION, value: \"%s\"}
                          ports:
                            - {name: http, containerPort: 8080}
                          readinessProbe:
                            httpGet: {path: /actuator/health/readiness, port: http}
                          livenessProbe:
                            httpGet: {path: /actuator/health/liveness, port: http}
                          resources:
                            requests: {cpu: 100m, memory: 256Mi}
                            limits: {cpu: \"1\", memory: 1Gi}
                          securityContext:
                            allowPrivilegeEscalation: false
                            readOnlyRootFilesystem: true
                            runAsNonRoot: true
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  selector: {app.kubernetes.io/name: %s}
                  ports: [{name: http, port: 8080, targetPort: http}]
                """.formatted(e.namespace(),sa,e.namespace(),azureSa,name,e.namespace(),name,yaml(agent),yaml(version),replicas,name,name,azurePod,sa,yaml(image),yaml(agent),yaml(version),name,e.namespace(),name);
        if(cron==null||cron.isBlank())return base;
        validateCron(cron);
        return base+"""
                ---
                apiVersion: batch/v1
                kind: CronJob
                metadata:
                  name: %s
                  namespace: %s
                spec:
                  schedule: \"%s\"
                  timeZone: \"%s\"
                  concurrencyPolicy: Forbid
                  jobTemplate:
                    spec:
                      ttlSecondsAfterFinished: 60
                      backoffLimit: 1
                      template:
                        spec:
                          serviceAccountName: %s
                          restartPolicy: Never
                          containers:
                            - name: invoke
                              image: curlimages/curl:8.12.1
                              args: [\"-fsS\", \"-X\", \"POST\", \"http://%s:8080/invoke\"]
                """.formatted(name+"-schedule",e.namespace(),yaml(cron.trim()),yaml(blankDefault(zone,"UTC")),sa,name);
    }

    private static String studioPlan(String agent,String version){return "# Agent Studio managed runtime\nagentId: "+yaml(agent)+"\nagentVersion: "+yaml(version)+"\nstrategy: PINNED_VERSION\n";}
    private static void validateCron(String cron){if(cron==null||cron.isBlank()||cron.trim().split("\\s+").length!=5)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"cronExpression must contain five fields");}
    private static void requireConfig(Map<String,Object> config,String key){if(config==null||stringValue(config.get(key)).isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" is required for Azure");}
    private static String enumValue(String value,List<String> allowed,String field){String normalized=required(value).toUpperCase();if(!allowed.contains(normalized))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,field+" is invalid");return normalized;}
    private static String slug(String value){String normalized=required(value).toLowerCase().replaceAll("[^a-z0-9.-]+","-").replaceAll("(^-|-$)","");if(normalized.length()<3||normalized.length()>128)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"identifier must be 3-128 characters");return normalized;}
    private static String kubeName(String value){String name=slug(value).replace('.','-');return name.length()>63?name.substring(0,63).replaceAll("-$",""):name;}
    private static String required(String value){if(value==null||value.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"required value is missing");return value.trim();}
    private static String blankDefault(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String stringValue(Object value){return value==null?"":String.valueOf(value);}
    private static String yaml(String value){return value.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ").replace("\r"," ");}
    private static String maskRef(String value){if(value==null)return null;int marker=value.indexOf("://");return marker<0?"configured":value.substring(0,marker+3)+"••••••";}
    private String write(Object value){try{return json.writeValueAsString(value==null?Map.of():value);}catch(JsonProcessingException e){throw new IllegalArgumentException("configuration is not valid JSON",e);}}
    @SuppressWarnings("unchecked") private Map<String,Object> readMap(String value){try{return json.readValue(value,Map.class);}catch(JsonProcessingException e){throw new IllegalStateException("stored deployment configuration is invalid",e);}}

    record EnvironmentRecord(String environmentId,String targetType,String cloudProvider,String namespace,Map<String,Object> adapterConfig,String credentialRef){}
    public record EnvironmentInput(@NotBlank String environmentId,@NotBlank String displayName,@NotBlank String targetType,@NotBlank String cloudProvider,@NotBlank String namespace,Map<String,Object> adapterConfig,String credentialRef){}
    public record EnvironmentView(String environmentId,String displayName,String targetType,String cloudProvider,String namespace,Map<String,Object> adapterConfig,String credentialRef,String status,Instant updatedAt){}
    public record DeploymentInput(@NotBlank String environmentId,@NotBlank String agentId,String agentVersion,String imageRef,@Min(0) @Max(100) Integer replicas,String cronExpression,String timeZone,Map<String,Object> scheduleInput,String brainProfile){}
    public record DeploymentView(String deploymentId,String environmentId,String agentId,String agentVersion,String deploymentMode,String desiredState,String observedState,String imageRef,int replicas,String manifestYaml,Map<String,Object> configuration,String lastError,Instant updatedAt){}
    public record ScheduleInput(@NotBlank String scheduleId,@NotBlank String deploymentId,@NotBlank String cronExpression,String timeZone,boolean enabled,Map<String,Object> input){}
    public record ScheduleView(String scheduleId,String deploymentId,String cronExpression,String timeZone,boolean enabled,Map<String,Object> input){}
    public record BrainInput(@NotBlank String displayName,String modelProfile,Map<String,Object> policy,boolean enabled){}
    public record BrainView(String profileId,String displayName,String modelProfile,Map<String,Object> policy,boolean enabled){}
}
