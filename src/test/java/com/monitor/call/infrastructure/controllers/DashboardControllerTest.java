package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.ports.in.DashboardUseCases;
import com.monitor.call.domain.responses.*;
import com.monitor.call.infrastructure.adapters.in.controllers.DashboardController;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = DashboardController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private DashboardUseCases dashUseCases;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserJpaRepository userJpaRepository;

    private static final String AUTH = "Bearer valid-token";

    // ─── GET /api/dashboard/agent/{extension} ────────────────────────────────

    @Test
    void agentDashboard_validExtension_returns200() throws Exception {
        AgentDashboardResponse resp = AgentDashboardResponse.builder()
                .extension("1001").agentName("Ana").isActive(true)
                .totalCalls(10L).answeredCalls(8L).missedCalls(2L).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(dashUseCases.getAgentDashboard(eq("1001"), any(), any())).thenReturn(resp);

        mvc.perform(get("/api/dashboard/agent/1001")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extension").value("1001"))
                .andExpect(jsonPath("$.agentName").value("Ana"));
    }

    @Test
    void agentDashboard_useCaseThrows_returns404() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(dashUseCases.getAgentDashboard(eq("9999"), any(), any()))
                .thenThrow(new RuntimeException("Extension no encontrado"));

        mvc.perform(get("/api/dashboard/agent/9999")
                .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/dashboard/admin ────────────────────────────────────────────

    @Test
    void adminDashboard_returns200() throws Exception {
        AdminDashboardResponse resp = AdminDashboardResponse.builder()
                .totalAgents(5).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(dashUseCases.getAdminDashboard(eq(10L), any(), any(), isNull(), isNull())).thenReturn(resp);

        mvc.perform(get("/api/dashboard/admin")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAgents").value(5));
    }

    @Test
    void adminDashboard_withGroupId_passesGroupId() throws Exception {
        AdminDashboardResponse resp = AdminDashboardResponse.builder()
                .totalAgents(2).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(dashUseCases.getAdminDashboard(eq(10L), any(), any(), eq(5L), isNull())).thenReturn(resp);

        mvc.perform(get("/api/dashboard/admin")
                .param("groupId", "5")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());

        verify(dashUseCases).getAdminDashboard(eq(10L), any(), any(), eq(5L), isNull());
    }

    // ─── GET /api/dashboard/status ───────────────────────────────────────────

    @Test
    void agentStatus_returns200() throws Exception {
        AgentStatusResponse resp = AgentStatusResponse.builder()
                .agents(List.of()).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(dashUseCases.getAgentStatus(eq(10L), isNull())).thenReturn(resp);

        mvc.perform(get("/api/dashboard/status")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void agentStatus_withGroupId_passesGroupId() throws Exception {
        AgentStatusResponse resp = AgentStatusResponse.builder()
                .agents(List.of()).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(dashUseCases.getAgentStatus(eq(10L), eq(3L))).thenReturn(resp);

        mvc.perform(get("/api/dashboard/status")
                .param("groupId", "3")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    // ─── GET /api/dashboard/sales ────────────────────────────────────────────

    @Test
    void salesDashboard_returns200() throws Exception {
        SalesDashboardResponse resp = SalesDashboardResponse.builder()
                .ownerId(5L).totalLeads(20L).build();
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(dashUseCases.getSalesDashboard(eq(5L), any(), any())).thenReturn(resp);

        mvc.perform(get("/api/dashboard/sales")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(5));
    }
}
