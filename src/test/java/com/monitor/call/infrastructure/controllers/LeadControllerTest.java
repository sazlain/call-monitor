package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.responses.BulkLeadResponse;
import com.monitor.call.domain.responses.LeadResponse;
import com.monitor.call.infrastructure.adapters.in.controllers.LeadController;
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
    controllers = LeadController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class LeadControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private LeadUseCases leadUseCases;
    @MockitoBean private JwtUtil jwtUtil;

    private static final String AUTH = "Bearer valid-token";

    private LeadResponse buildLead(Long id, String name, LeadStatus status) {
        return LeadResponse.builder()
                .id(id).contactName(name).contactPhone("5551000")
                .status(status).build();
    }

    // ─── POST /api/leads ─────────────────────────────────────────────────────

    @Test
    void create_validRequest_returns201() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(leadUseCases.createLead(any(), eq(5L)))
                .thenReturn(buildLead(1L, "Juan", LeadStatus.NEW));

        mvc.perform(post("/api/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"contactName":"Juan","contactPhone":"5551000",
                         "leadDate":"2026-05-01","leadSource":"WEB"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactName").value("Juan"));
    }

    @Test
    void create_missingContactName_returns400() throws Exception {
        mvc.perform(post("/api/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"contactPhone":"5551000","leadDate":"2026-05-01"}
                        """))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/leads ──────────────────────────────────────────────────────

    @Test
    void list_returnsLeads() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(leadUseCases.listLeadsByOwner(eq(5L), isNull(), isNull(), isNull()))
                .thenReturn(List.of(buildLead(1L, "Juan", LeadStatus.NEW)));

        mvc.perform(get("/api/leads")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void list_withStatusFilter_passesStatusToUseCase() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(leadUseCases.listLeadsByOwner(eq(5L), eq(LeadStatus.CALLBACK), isNull(), isNull()))
                .thenReturn(List.of(buildLead(2L, "Ana", LeadStatus.CALLBACK)));

        mvc.perform(get("/api/leads")
                .param("status", "CALLBACK")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    // ─── GET /api/leads/assigned ─────────────────────────────────────────────

    @Test
    void queue_returnsAssignedLeads() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(leadUseCases.listAssignedLeads(5L))
                .thenReturn(List.of(buildLead(3L, "Pedro", LeadStatus.PENDING)));

        mvc.perform(get("/api/leads/assigned")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));
    }

    // ─── GET /api/leads/callbacks ────────────────────────────────────────────

    @Test
    void listCallbacks_returnsCallbackLeads() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(leadUseCases.listPendingCallbacks(5L))
                .thenReturn(List.of(buildLead(4L, "Maria", LeadStatus.CALLBACK)));

        mvc.perform(get("/api/leads/callbacks")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CALLBACK"));
    }

    // ─── GET /api/leads/{leadId} ─────────────────────────────────────────────

    @Test
    void get_exists_returnsLead() throws Exception {
        when(leadUseCases.getLead(1L)).thenReturn(buildLead(1L, "Juan", LeadStatus.NEW));

        mvc.perform(get("/api/leads/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(leadUseCases.getLead(99L))
                .thenThrow(new RuntimeException("Lead no encontrado"));

        mvc.perform(get("/api/leads/99"))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/leads/{leadId} ─────────────────────────────────────────────

    @Test
    void update_validRequest_returnsUpdated() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(leadUseCases.updateLead(eq(1L), any(), eq(5L)))
                .thenReturn(buildLead(1L, "Updated", LeadStatus.PENDING));

        mvc.perform(put("/api/leads/1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"contactName":"Updated","contactPhone":"5551001",
                         "leadDate":"2026-05-01"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactName").value("Updated"));
    }

    // ─── PUT /api/leads/{leadId}/assign ──────────────────────────────────────

    @Test
    void assign_validRequest_returnsUpdatedLead() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        when(leadUseCases.assignLead(1L, 10L, 5L))
                .thenReturn(buildLead(1L, "Juan", LeadStatus.PENDING));

        mvc.perform(put("/api/leads/1/assign")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"assignedAgentId":10}
                        """))
                .andExpect(status().isOk());
    }

    // ─── DELETE /api/leads/{leadId} ──────────────────────────────────────────

    @Test
    void discard_validRequest_returns204() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        doNothing().when(leadUseCases).discardLead(1L, 5L);

        mvc.perform(delete("/api/leads/1")
                .header("Authorization", AUTH))
                .andExpect(status().isNoContent());
    }

    @Test
    void discard_notFound_returns404() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(5L);
        doThrow(new RuntimeException("Lead no encontrado"))
                .when(leadUseCases).discardLead(99L, 5L);

        mvc.perform(delete("/api/leads/99")
                .header("Authorization", AUTH))
                .andExpect(status().isNotFound());
    }
}
