package dev.agentstudio.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioBootstrapController {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final String defaultOrganization;
    private final String defaultUser;

    StudioBootstrapController(JdbcClient jdbc, ObjectMapper json,
            @Value("${agent-studio.local-organization:local-development}") String defaultOrganization,
            @Value("${agent-studio.local-user:studio-user}") String defaultUser) {
        this.jdbc = jdbc;
        this.json = json;
        this.defaultOrganization = defaultOrganization;
        this.defaultUser = defaultUser;
    }

    @GetMapping("/bootstrap")
    Bootstrap bootstrap(
            @RequestHeader(value = "X-Tenant-Id", required = false) String organizationHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userHeader) {
        String organizationId = clean(orDefault(organizationHeader, defaultOrganization));
        String userId = clean(orDefault(userHeader, defaultUser));
        ensure(organizationId, userId);
        Organization organization = jdbc.sql("select organization_id,display_name,settings::text from organization_accounts where organization_id=? and status='ACTIVE'")
                .param(organizationId).query((rs, n) -> new Organization(rs.getString(1), rs.getString(2), map(rs.getString(3)))).single();
        User user = jdbc.sql("""
                select p.user_id,p.display_name,p.email,p.preferences::text,m.role
                from user_profiles p join organization_memberships m on m.organization_id=p.tenant_id and m.user_id=p.user_id
                where p.tenant_id=? and p.user_id=? and m.enabled=true
                """).params(organizationId, userId)
                .query((rs, n) -> new User(rs.getString(1), rs.getString(2), rs.getString(3), map(rs.getString(4)), rs.getString(5))).single();
        List<String> capabilities = jdbc.sql("select capability_id from capabilities where tenant_id=? order by capability_id")
                .param(organizationId).query(String.class).list();
        List<String> modelProfiles = jdbc.sql("select profile_id from model_profiles where tenant_id=? order by profile_id")
                .param(organizationId).query(String.class).list();
        return new Bootstrap(organization, user, capabilities, modelProfiles);
    }

    private void ensure(String organization, String user) {
        jdbc.sql("insert into organization_accounts(organization_id,display_name) values(?,?) on conflict do nothing")
                .params(organization, organization).update();
        jdbc.sql("insert into user_profiles(tenant_id,user_id,display_name) values(?,?,?) on conflict do nothing")
                .params(organization, user, user).update();
        jdbc.sql("insert into organization_memberships(organization_id,user_id,role) values(?,?,'OWNER') on conflict do nothing")
                .params(organization, user).update();
    }

    private Map<String, Object> map(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception ignored) { return Map.of(); }
    }

    private static String orDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String clean(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.isBlank() || clean.length() > 128 || !clean.matches("[A-Za-z0-9._-]+")) throw new IllegalArgumentException("invalid identity");
        return clean;
    }

    record Organization(String organizationId, String displayName, Map<String, Object> settings) {}
    record User(String userId, String displayName, String email, Map<String, Object> preferences, String role) {}
    record Bootstrap(Organization organization, User user, List<String> capabilities, List<String> modelProfiles) {}
}
