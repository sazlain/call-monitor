package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.ports.in.AppointmentUseCases;
import com.monitor.call.domain.responses.AppointmentResponse;
import com.monitor.call.infrastructure.adapters.in.controllers.AppointmentController;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AppointmentController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class AppointmentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AppointmentUseCases appointmentUseCases;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private AgentJpaRepository agentRepo;
    @MockitoBean private UserJpaRepository userJpaRepository;

    private static final String AUTH = "Bearer valid-token";

    private AppointmentResponse buildAppt(Long id, AppointmentStatus status) {
        return AppointmentResponse.builder()
                .id(id).leadId(5L).agentId(2L).agentName("Ana")
                .status(status).build();
    }

    private AgentEntity buildAgentEntity(Long agentId, Long userId) {
        return AgentEntity.builder()
                .id(agentId).userId(userId).extension("1001").active(true).build();
    }

    // ─── POST /api/appointments ──────────────────────────────────────────────

    @Test
    void create_validRequest_returns200() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(agentRepo.findByUserId(1L))
                .thenReturn(Optional.of(buildAgentEntity(2L, 1L)));
        when(appointmentUseCases.create(any(), eq(2L)))
                .thenReturn(buildAppt(1L, AppointmentStatus.SCHEDULED));

        mvc.perform(post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"leadId":5,"callId":"CALL-001",
                         "appointmentDate":"2026-05-20","appointmentTime":"16:30"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_agentNotFound_returns404() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(agentRepo.findByUserId(1L)).thenReturn(Optional.empty());

        mvc.perform(post("/api/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"leadId":5,"callId":"CALL-001","appointmentDate":"2026-05-20"}
                        """))
                .andExpect(status().isNotFound());
    }

    // ─── PUT /api/appointments/{id}/reschedule ───────────────────────────────

    @Test
    void reschedule_validRequest_returns200() throws Exception {
        when(appointmentUseCases.reschedule(eq(1L), any()))
                .thenReturn(buildAppt(1L, AppointmentStatus.SCHEDULED));

        mvc.perform(put("/api/appointments/1/reschedule")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"appointmentDate":"2026-06-01","appointmentTime":"10:00"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    // ─── PUT /api/appointments/{id}/cancel ──────────────────────────────────

    @Test
    void cancel_validId_returns200() throws Exception {
        when(appointmentUseCases.cancel(eq(1L), isNull()))
                .thenReturn(buildAppt(1L, AppointmentStatus.CANCELLED));

        mvc.perform(put("/api/appointments/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancel_withReason_passesReason() throws Exception {
        when(appointmentUseCases.cancel(eq(1L), eq("No answer")))
                .thenReturn(buildAppt(1L, AppointmentStatus.CANCELLED));

        mvc.perform(put("/api/appointments/1/cancel")
                .param("reason", "No answer"))
                .andExpect(status().isOk());
    }

    // ─── PUT /api/appointments/{id}/confirm ──────────────────────────────────

    @Test
    void confirm_validId_returns200() throws Exception {
        when(appointmentUseCases.confirm(1L))
                .thenReturn(buildAppt(1L, AppointmentStatus.CONFIRMED));

        mvc.perform(put("/api/appointments/1/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    // ─── GET /api/appointments/my ─────────────────────────────────────────────

    @Test
    void myAppointments_returnsAgentAppointments() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);
        when(agentRepo.findByUserId(1L))
                .thenReturn(Optional.of(buildAgentEntity(2L, 1L)));
        when(appointmentUseCases.listMyAppointments(2L))
                .thenReturn(List.of(buildAppt(1L, AppointmentStatus.SCHEDULED)));

        mvc.perform(get("/api/appointments/my")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ─── GET /api/appointments ────────────────────────────────────────────────

    @Test
    void all_returnsAllAppointments() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(appointmentUseCases.listAll(10L))
                .thenReturn(List.of(buildAppt(1L, AppointmentStatus.SCHEDULED),
                                    buildAppt(2L, AppointmentStatus.CONFIRMED)));

        mvc.perform(get("/api/appointments")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ─── GET /api/appointments/lead/{leadId} ─────────────────────────────────

    @Test
    void byLead_returnsLeadAppointments() throws Exception {
        when(appointmentUseCases.listByLead(5L))
                .thenReturn(List.of(buildAppt(1L, AppointmentStatus.SCHEDULED)));

        mvc.perform(get("/api/appointments/lead/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].leadId").value(5));
    }

    // ─── PUT /api/appointments/{id}/attend ───────────────────────────────────

    @Test
    void attend_validId_returns200() throws Exception {
        when(appointmentUseCases.attend(1L))
                .thenReturn(buildAppt(1L, AppointmentStatus.ATTENDED));

        mvc.perform(put("/api/appointments/1/attend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATTENDED"));
    }
}
