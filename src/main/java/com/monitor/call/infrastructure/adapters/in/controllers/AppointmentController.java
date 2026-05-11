package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.AppointmentUseCases;
import com.monitor.call.domain.responses.AppointmentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.requests.AppointmentRequest;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appointments")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Appointments", description = "Gestión de citas agendadas")
public class AppointmentController {

    private final AppointmentUseCases appointmentUseCases;
    private final JwtUtil             jwtUtil;
    private final AgentJpaRepository  agentRepo;

    public AppointmentController(AppointmentUseCases appointmentUseCases,
                                  JwtUtil jwtUtil,
                                  AgentJpaRepository agentRepo) {
        this.appointmentUseCases = appointmentUseCases;
        this.jwtUtil             = jwtUtil;
        this.agentRepo           = agentRepo;
    }

    @Operation(summary = "Crear cita desde tipificación")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<AppointmentResponse> create(
            @RequestHeader("Authorization") String auth,
            @RequestBody AppointmentRequest request) {
        Long userId  = jwtUtil.extractUserId(auth.substring(7));
        Long agentId = agentRepo.findByUserId(userId)
                .map(a -> a.getId())
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));
        return ResponseEntity.ok(appointmentUseCases.create(request, agentId));
    }

    @Operation(summary = "Reagendar cita")
    @PutMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable Long id,
            @RequestBody AppointmentRequest request) {
        return ResponseEntity.ok(appointmentUseCases.reschedule(id, request));
    }

    @Operation(summary = "Cancelar cita")
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(appointmentUseCases.cancel(id, reason));
    }

    @Operation(summary = "Confirmar cita — lead pasa a CONVERTED")
    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<AppointmentResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentUseCases.confirm(id));
    }

    @Operation(summary = "Mis citas — agente autenticado")
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<List<AppointmentResponse>> myAppointments(
            @RequestHeader("Authorization") String auth) {
        Long userId  = jwtUtil.extractUserId(auth.substring(7));
        Long agentId = agentRepo.findByUserId(userId)
                .map(a -> a.getId())
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));
        return ResponseEntity.ok(appointmentUseCases.listMyAppointments(agentId));
    }

    @Operation(summary = "Todas las citas — admin")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponse>> all(
            @RequestHeader("Authorization") String auth) {
        Long userId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(appointmentUseCases.listAll(userId));
    }

    @Operation(summary = "Citas de un lead")
    @GetMapping("/lead/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT','SALES_AGENT')")
    public ResponseEntity<List<AppointmentResponse>> byLead(@PathVariable Long leadId) {
        return ResponseEntity.ok(appointmentUseCases.listByLead(leadId));
    }

    @Operation(summary = "Marcar cita como atendida — estado final exitoso")
    @PutMapping("/{id}/attend")
    @PreAuthorize("hasAnyRole('ADMIN','CALL_AGENT')")
    public ResponseEntity<AppointmentResponse> attend(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentUseCases.attend(id));
    }
}
