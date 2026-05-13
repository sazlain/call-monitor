package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.ports.in.AgentUseCases;
import com.monitor.call.domain.responses.AgentResponse;
import com.monitor.call.infrastructure.adapters.in.controllers.AgentController;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AgentController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AgentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AgentUseCases agentUseCases;
    @MockitoBean private JwtUtil jwtUtil;

    private static final String AUTH = "Bearer valid-token";

    // ─── POST /api/agents ────────────────────────────────────────────────────

    @Test
    void create_validRequest_returns201() throws Exception {
        AgentResponse resp = AgentResponse.builder()
                .id(1L).extension("1001").name("Ana").email("ana@test.com").active(true).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(agentUseCases.createAgent(anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(resp);

        mvc.perform(post("/api/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"name":"Ana","email":"ana@test.com","extension":"1001","groupId":5}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extension").value("1001"));
    }

    @Test
    void create_missingName_returns400() throws Exception {
        mvc.perform(post("/api/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"email":"ana@test.com","extension":"1001","groupId":5}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_missingExtension_returns400() throws Exception {
        mvc.perform(post("/api/agents")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"name":"Ana","email":"ana@test.com","groupId":5}
                        """))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/agents ─────────────────────────────────────────────────────

    @Test
    void list_withoutGroupId_returnsListByAdmin() throws Exception {
        AgentResponse resp = AgentResponse.builder()
                .id(1L).extension("1001").name("Ana").email("ana@test.com").active(true).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(agentUseCases.listAgentsByAdmin(10L)).thenReturn(List.of(resp));

        mvc.perform(get("/api/agents")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].extension").value("1001"));
    }

    @Test
    void list_withGroupId_returnsListByGroup() throws Exception {
        AgentResponse resp = AgentResponse.builder()
                .id(2L).extension("1002").name("Bob").email("bob@test.com").active(true).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(agentUseCases.listAgentsByGroup(5L)).thenReturn(List.of(resp));

        mvc.perform(get("/api/agents")
                .param("groupId", "5")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].extension").value("1002"));
    }

    // ─── GET /api/agents/{agentId} ───────────────────────────────────────────

    @Test
    void get_exists_returnsAgent() throws Exception {
        AgentResponse resp = AgentResponse.builder()
                .id(1L).extension("1001").name("Ana").email("ana@test.com").active(true).build();
        when(agentUseCases.getAgent(1L)).thenReturn(resp);

        mvc.perform(get("/api/agents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(agentUseCases.getAgent(99L))
                .thenThrow(new RuntimeException("Agente no encontrado"));

        mvc.perform(get("/api/agents/99"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/agents/extension/{extension} ───────────────────────────────

    @Test
    void getByExtension_exists_returnsAgent() throws Exception {
        AgentResponse resp = AgentResponse.builder()
                .id(1L).extension("1001").name("Ana").email("ana@test.com").active(true).build();
        when(agentUseCases.getAgentByExtension("1001")).thenReturn(resp);

        mvc.perform(get("/api/agents/extension/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension").value("1001"));
    }

    // ─── PUT /api/agents/{agentId} ───────────────────────────────────────────

    @Test
    void update_validRequest_returnsUpdated() throws Exception {
        AgentResponse resp = AgentResponse.builder()
                .id(1L).extension("1002").name("Ana Updated").email("ana@test.com").active(true).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(agentUseCases.updateAgent(eq(1L), anyString(), anyString(), eq(10L))).thenReturn(resp);

        mvc.perform(put("/api/agents/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"name":"Ana Updated","extension":"1002"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension").value("1002"));
    }

    // ─── DELETE /api/agents/{agentId} ────────────────────────────────────────

    @Test
    void deactivate_validRequest_returns204() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        doNothing().when(agentUseCases).deactivateAgent(1L, 10L);

        mvc.perform(delete("/api/agents/1")
                .header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void deactivate_notFound_returns404() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        doThrow(new RuntimeException("Agente no encontrado"))
                .when(agentUseCases).deactivateAgent(99L, 10L);

        mvc.perform(delete("/api/agents/99")
                .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }
}
