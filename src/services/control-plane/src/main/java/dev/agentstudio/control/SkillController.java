package dev.agentstudio.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public SkillController(JdbcClient jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    @GetMapping
    List<SkillView> list(@RequestHeader("X-Tenant-Id") String tenant) {
        return jdbc.sql("select skill_id,display_name,description,reference,tags from available_skills where tenant_id=? order by display_name")
                .param(new TenantContext(tenant).tenantId())
                .query((rs,n)->new SkillView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),tags(rs.getString(5)))).list();
    }
    private Set<String> tags(String value) { try { return Arrays.stream(json.readValue(value,String[].class)).collect(Collectors.toUnmodifiableSet()); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    record SkillView(String skillId,String displayName,String description,String reference,Set<String> tags) {}
}
