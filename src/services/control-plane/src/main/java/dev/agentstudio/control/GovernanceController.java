package dev.agentstudio.control;

import dev.agentstudio.domain.Capability;
import dev.agentstudio.domain.ModelProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class GovernanceController {
    private final GovernanceCatalog catalog;
    private final JdbcClient jdbc;
    public GovernanceController(GovernanceCatalog catalog,JdbcClient jdbc) { this.catalog = catalog; this.jdbc=jdbc; }

    @GetMapping("/capabilities")
    List<Capability> capabilities(@RequestHeader("X-Tenant-Id") String tenant) { return catalog.capabilities(new TenantContext(tenant).tenantId()); }
    @PostMapping("/capabilities") @ResponseStatus(HttpStatus.CREATED)
    Capability capability(@RequestHeader("X-Tenant-Id") String tenant, @RequestBody CapabilityInput r) {
        return catalog.createCapability(new Capability(new TenantContext(tenant).tenantId(), r.capabilityId(), r.displayName(), r.description(), r.kind(), r.inputSchemaRef(), r.outputSchemaRef(), r.riskClass(), r.owner(), r.tags()));
    }
    @GetMapping("/model-profiles")
    List<ModelProfile> profiles(@RequestHeader("X-Tenant-Id") String tenant) { return catalog.modelProfiles(new TenantContext(tenant).tenantId()); }
    @PostMapping("/model-profiles") @ResponseStatus(HttpStatus.CREATED)
    ModelProfile profile(@RequestHeader("X-Tenant-Id") String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user") String user, @RequestBody ModelProfileInput r) {
        if(looksLikeCredential(r.modelId()))throw new IllegalArgumentException("modelId looks like a credential; API keys must be stored in a model connection");
        String tenantId=new TenantContext(tenant).tenantId(); ModelProfile created=catalog.createModelProfile(new ModelProfile(tenantId, r.id(), r.qualityTier(), r.latencyTier(), r.maxInputTokens(), r.maxOutputTokens(), r.requiredFeatures(), r.fallbackPolicy(), r.maxCostPerCall(), r.connectionId(), r.modelId(), r.generationParameters()));
        jdbc.sql("update model_profiles set owner_user_id=? where tenant_id=? and profile_id=?").params(user,tenantId,r.id()).update();
        return created;
    }
    private static boolean looksLikeCredential(String value){if(value==null)return false;String normalized=value.trim().toLowerCase();return normalized.startsWith("sk-")||normalized.startsWith("bearer ")||normalized.startsWith("api_key")||normalized.startsWith("apikey");}
    @DeleteMapping("/model-profiles/{profileId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteProfile(@RequestHeader("X-Tenant-Id")String tenant,@RequestHeader(value="X-User-Id",defaultValue="studio-user")String user,@PathVariable String profileId){
        String tenantId=new TenantContext(tenant).tenantId();
        int deleted=jdbc.sql("delete from model_profiles where tenant_id=? and profile_id=?").params(tenantId,profileId).update();
        if(deleted==0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"model profile not found");
        String defaultProfile=jdbc.sql("select preferences->>'defaultModelProfile' from user_profiles where tenant_id=? and user_id=?").params(tenantId,user).query(String.class).optional().orElse(null);
        if(profileId.equals(defaultProfile))jdbc.sql("update user_profiles set preferences=preferences-'defaultModelProfile',updated_at=now() where tenant_id=? and user_id=?").params(tenantId,user).update();
    }
    record CapabilityInput(String capabilityId, String displayName, String description, Capability.Kind kind,
            String inputSchemaRef, String outputSchemaRef, Capability.RiskClass riskClass, String owner, Set<String> tags) {}
    record ModelProfileInput(String id, ModelProfile.QualityTier qualityTier, ModelProfile.LatencyTier latencyTier,
            Integer maxInputTokens, Integer maxOutputTokens, Set<ModelProfile.Feature> requiredFeatures,
            ModelProfile.FallbackPolicy fallbackPolicy, BigDecimal maxCostPerCall, String connectionId,
            String modelId, Map<String,Object> generationParameters) {}
}
