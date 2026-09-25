package dev.agentstudio.control;

import dev.agentstudio.domain.AgentDefinition;
import dev.agentstudio.domain.AgentVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final AgentRegistry registry;
    private final JdbcClient jdbc;
    public AgentController(AgentRegistry registry,JdbcClient jdbc) { this.registry = registry; this.jdbc=jdbc; }

    @GetMapping public List<AgentDefinition> list(@RequestHeader("X-Tenant-Id") String tenantId) { return registry.list(new TenantContext(tenantId).tenantId()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AgentDefinition create(@RequestHeader("X-Tenant-Id") String tenantId,@RequestHeader(value="X-User-Id",defaultValue="studio-user") String userId, @Valid @RequestBody CreateAgent request) {
        Instant now = Instant.now();
        String tenant=new TenantContext(tenantId).tenantId();
        AgentDefinition created=registry.create(new AgentDefinition(tenant, request.id(), request.displayName(), request.description(), request.ownerTeam(), request.tags(), request.interactionMode(), request.topology(), request.triggerMode(), AgentDefinition.Status.DRAFT, now, now));
        jdbc.sql("update agents set owner_user_id=? where tenant_id=? and id=?").params(userId,tenant,request.id()).update();
        return created;
    }
    @PutMapping("/{agentId}")
    public AgentDefinition update(@RequestHeader("X-Tenant-Id") String tenantId,@PathVariable String agentId,@Valid @RequestBody CreateAgent request) {
        if(!agentId.equals(request.id())) throw new IllegalArgumentException("path agentId must match body id");
        String tenant=new TenantContext(tenantId).tenantId();
        AgentDefinition current=registry.list(tenant).stream().filter(a->a.id().equals(agentId)).findFirst().orElseThrow(()->new IllegalArgumentException("agent not found"));
        return registry.update(new AgentDefinition(tenant,agentId,request.displayName(),request.description(),request.ownerTeam(),request.tags(),request.interactionMode(),request.topology(),request.triggerMode(),current.status(),current.createdAt(),Instant.now()));
    }
    @GetMapping("/{agentId}/versions") public List<AgentVersion> versions(@RequestHeader("X-Tenant-Id") String tenantId, @PathVariable String agentId) { return registry.versions(new TenantContext(tenantId).tenantId(), agentId); }
    @PostMapping("/{agentId}/versions") @ResponseStatus(HttpStatus.CREATED)
    public AgentVersion createVersion(@RequestHeader("X-Tenant-Id") String tenantId,@RequestHeader(value="X-User-Id",defaultValue="studio-user") String userId, @PathVariable String agentId, @RequestBody AgentVersionInput r) {
        if (!agentId.equals(r.agentId())) throw new IllegalArgumentException("path agentId must match body agentId");
        String tenant=new TenantContext(tenantId).tenantId(); AgentVersion created=registry.createVersion(r.toDomain(tenant));
        jdbc.sql("update agent_versions set created_by_user_id=? where tenant_id=? and agent_id=? and version=?").params(userId,tenant,agentId,r.version()).update();
        return created;
    }
    @PostMapping("/{agentId}/versions/{version}/{action}")
    public AgentVersion transition(@RequestHeader("X-Tenant-Id") String tenantId, @PathVariable String agentId, @PathVariable String version, @PathVariable String action) {
        AgentVersion.Lifecycle target = switch (action) { case "validate" -> AgentVersion.Lifecycle.VALIDATED; case "canary" -> AgentVersion.Lifecycle.CANARY; case "activate" -> AgentVersion.Lifecycle.ACTIVE; case "retire" -> AgentVersion.Lifecycle.RETIRED; case "revoke" -> AgentVersion.Lifecycle.REVOKED; default -> throw new IllegalArgumentException("unknown lifecycle action"); };
        return registry.transition(new TenantContext(tenantId).tenantId(), agentId, version, target);
    }
    @DeleteMapping("/{agentId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void delete(@RequestHeader("X-Tenant-Id")String tenantId,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String userId,@PathVariable String agentId){
        String tenant=new TenantContext(tenantId).tenantId();
        Integer owned=jdbc.sql("select count(*) from agents where tenant_id=? and id=? and owner_user_id=?").params(tenant,agentId,userId).query(Integer.class).single();
        if(owned==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"agent not found or is system-owned");
        Integer runs=jdbc.sql("select count(*) from runs where tenant_id=? and agent_id=?").params(tenant,agentId).query(Integer.class).single();
        jdbc.sql("delete from agent_release_state where tenant_id=? and agent_id=?").params(tenant,agentId).update();
        if(runs>0){
            // Preserve immutable versions referenced by historical runs, but remove the
            // definition from discovery and prevent new executions from resolving it.
            jdbc.sql("update agents set status='ARCHIVED',updated_at=now() where tenant_id=? and id=? and owner_user_id=?")
                    .params(tenant,agentId,userId).update();
            return;
        }
        jdbc.sql("delete from agent_versions where tenant_id=? and agent_id=?").params(tenant,agentId).update();
        jdbc.sql("delete from agents where tenant_id=? and id=? and owner_user_id=?").params(tenant,agentId,userId).update();
    }
    public record CreateAgent(@NotBlank String id, @NotBlank String displayName, String description,
            @NotBlank String ownerTeam, Set<String> tags, AgentDefinition.InteractionMode interactionMode,
            AgentDefinition.Topology topology, AgentDefinition.TriggerMode triggerMode) {}
    public record AgentVersionInput(String agentId, String version, AgentVersion.RuntimeType runtimeType, AgentVersion.HostingMode hostingMode,
            String artifactRef, String remoteEndpointRef, Set<String> capabilitiesProvided, Set<String> toolCapabilitiesRequired,
            Set<String> agentCapabilitiesRequired, String modelProfile, String promptRef, String inputSchemaRef, String outputSchemaRef,
            String executionPolicyRef, String securityPolicyRef, String checksum) {
        AgentVersion toDomain(String tenant) { return new AgentVersion(tenant, agentId, version, runtimeType, hostingMode, artifactRef, remoteEndpointRef, capabilitiesProvided, toolCapabilitiesRequired, agentCapabilitiesRequired, modelProfile, promptRef, inputSchemaRef, outputSchemaRef, executionPolicyRef, securityPolicyRef, checksum, AgentVersion.Lifecycle.CREATED, Instant.now()); }
    }
}
