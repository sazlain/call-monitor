package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.ports.in.ReportUseCases;
import com.monitor.call.infrastructure.adapters.in.controllers.ReportController;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = ReportController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class ReportControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private ReportUseCases reportUseCases;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserJpaRepository userJpaRepository;

    private static final String AUTH = "Bearer valid-token";

    // ─── GET /api/reports/agent/{extension}/calls ─────────────────────────

    @Test
    void agentCallReport_returnsCSV() throws Exception {
        byte[] csvData = "fecha,callId,numero\n2026-05-01,CALL-001,5551001\n".getBytes();
        when(reportUseCases.generateAgentCallReport(eq("1001"), any(), any()))
                .thenReturn(csvData);

        mvc.perform(get("/api/reports/agent/1001/calls"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")));
    }

    // ─── GET /api/reports/group ───────────────────────────────────────────

    @Test
    void groupReport_returnsCSV() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        byte[] csvData = "agente,total\nAna,10\n".getBytes();
        when(reportUseCases.generateGroupReport(eq(1L), isNull(), any(), any()))
                .thenReturn(csvData);

        mvc.perform(get("/api/reports/group")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }
}
