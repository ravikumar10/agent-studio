package dev.agentstudio.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import dev.agentstudio.domain.AgentDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AgentController.class)
class AgentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AgentRegistry registry;
    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS) JdbcClient jdbc;
    @Test void requiresTenantHeader() throws Exception {
        mvc.perform(post("/api/v1/agents").contentType("application/json").content("{\"id\":\"agent-one\",\"displayName\":\"One\",\"ownerTeam\":\"platform\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void createsTenantScopedDraft() throws Exception {
        when(registry.create(any())).thenAnswer(i -> i.getArgument(0, AgentDefinition.class));
        mvc.perform(post("/api/v1/agents").header("X-Tenant-Id", "tenant-a").contentType("application/json")
                .content("{\"id\":\"agent-one\",\"displayName\":\"One\",\"ownerTeam\":\"platform\"}"))
                .andExpect(status().isCreated());
        verify(registry).create(argThat(a -> a.tenantId().equals("tenant-a") && a.status() == AgentDefinition.Status.DRAFT));
    }
}
