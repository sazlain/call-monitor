package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.ports.in.AgentGroupUseCases;
import com.monitor.call.domain.responses.AgentGroupResponse;
import com.monitor.call.infrastructure.adapters.in.controllers.AgentGroupController;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
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
    controllers = AgentGroupController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AgentGroupControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AgentGroupUseCases groupUseCases;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserJpaRepository userJpaRepository;

    private static final String AUTH = "Bearer valid-token";

    private AgentGroupResponse buildGroup(Long id, String name) {
        return AgentGroupResponse.builder()
                .id(id).name(name).adminId(10L).active(true).agentCount(0)
                .agents(List.of()).build();
    }

    // ─── POST /api/groups ────────────────────────────────────────────────────

    @Test
    void create_validRequest_returns201() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(groupUseCases.createGroup(eq("Sales"), anyString(), eq(10L)))
                .thenReturn(buildGroup(1L, "Sales"));

        mvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"name":"Sales","description":"Sales team"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sales"));
    }

    @Test
    void create_missingName_returns400() throws Exception {
        mvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"description":"No name"}
                        """))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/groups ─────────────────────────────────────────────────────

    @Test
    void list_returnsGroupsByAdmin() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(groupUseCases.listGroupsByAdmin(10L)).thenReturn(List.of(buildGroup(1L, "Sales")));

        mvc.perform(get("/api/groups")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sales"));
    }

    // ─── GET /api/groups/{groupId} ───────────────────────────────────────────

    @Test
    void getGroup_exists_returnsGroup() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(groupUseCases.getGroup(1L, 10L)).thenReturn(buildGroup(1L, "Support"));

        mvc.perform(get("/api/groups/1")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getGroup_notFound_returns404() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(groupUseCases.getGroup(99L, 10L))
                .thenThrow(new RuntimeException("Grupo no encontrado"));

        mvc.perform(get("/api/groups/99")
                .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/groups/{groupId} ───────────────────────────────────────────

    @Test
    void update_validRequest_returnsUpdated() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(groupUseCases.updateGroup(eq(1L), eq("Sales Updated"), anyString(), eq(10L)))
                .thenReturn(buildGroup(1L, "Sales Updated"));

        mvc.perform(put("/api/groups/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"name":"Sales Updated","description":"desc"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sales Updated"));
    }

    // ─── DELETE /api/groups/{groupId} ────────────────────────────────────────

    @Test
    void deactivate_validRequest_returns204() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        doNothing().when(groupUseCases).deactivateGroup(1L, 10L);

        mvc.perform(delete("/api/groups/1")
                .header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    // ─── POST /api/groups/{groupId}/agents/{agentId} ─────────────────────────

    @Test
    void assignAgent_returnsUpdatedGroup() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(groupUseCases.assignAgentToGroup(1L, 2L, 10L)).thenReturn(buildGroup(1L, "Sales"));

        mvc.perform(post("/api/groups/1/agents/2")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ─── DELETE /api/groups/{groupId}/agents/{agentId} ───────────────────────

    @Test
    void removeAgent_returnsUpdatedGroup() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(groupUseCases.removeAgentFromGroup(1L, 2L, 10L)).thenReturn(buildGroup(1L, "Sales"));

        mvc.perform(delete("/api/groups/1/agents/2")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }
}
