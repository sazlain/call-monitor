package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.DashboardUseCases;
import com.monitor.call.domain.responses.*;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Estadisticas y monitoreo en tiempo real")
public class DashboardController {

    private final DashboardUseCases dashUseCases;
    private final JwtUtil jwtUtil;

    public DashboardController(DashboardUseCases dashUseCases, JwtUtil jwtUtil) {
        this.dashUseCases = dashUseCases;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Dashboard del agente de llamadas.
     * CALL_AGENT solo puede ver su propia extension.
     * ADMIN puede ver cualquier extension.
     */
    @GetMapping("/agent/{extension}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT')")
    @Operation(summary = "Dashboard individual del agente. KPIs, tendencias y llamadas recientes.")
    public ResponseEntity<AgentDashboardResponse> agentDashboard(
            @PathVariable String extension,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestHeader("Authorization") String auth) {

        OffsetDateTime[] range = resolveRange(from, to);
        return ResponseEntity.ok(dashUseCases.getAgentDashboard(extension, range[0], range[1]));
    }

    /**
     * Dashboard del administrador.
     * Vista consolidada de todos sus grupos y agentes.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dashboard admin. KPIs globales, ranking de agentes, tendencia diaria y alertas.")
    public ResponseEntity<AdminDashboardResponse> adminDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String extension,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        OffsetDateTime[] range = resolveRange(from, to);
        return ResponseEntity.ok(dashUseCases.getAdminDashboard(adminId, range[0], range[1], groupId, extension));
    }

    /**
     * Estado en tiempo real de los agentes.
     * Pensado para polling cada ~10 segundos desde el frontend.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT')")
    @Operation(summary = "Snapshot en tiempo real: quien esta en llamada ahora mismo.")
    public ResponseEntity<AgentStatusResponse> agentStatus(
            @RequestParam(required = false) Long groupId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(dashUseCases.getAgentStatus(adminId, groupId));
    }

    /**
     * Dashboard del agente de ventas.
     * Funnel de leads, conversion por origen, callbacks pendientes.
     */
    @GetMapping("/sales")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Dashboard de ventas. Funnel de leads, conversion y callbacks pendientes.")
    public ResponseEntity<SalesDashboardResponse> salesDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestHeader("Authorization") String auth) {

        Long ownerId = jwtUtil.extractUserId(auth.substring(7));
        OffsetDateTime[] range = resolveRange(from, to);
        return ResponseEntity.ok(dashUseCases.getSalesDashboard(ownerId, range[0], range[1]));
    }

    @GetMapping("/adherence")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cumplimiento de horarios por agente y día.")
    public ResponseEntity<List<ScheduleAdherenceRow>> scheduleAdherence(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long agentId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        LocalDate resolvedFrom = from != null ? from : LocalDate.now().minusDays(6);
        LocalDate resolvedTo   = to   != null ? to   : LocalDate.now();
        return ResponseEntity.ok(dashUseCases.getScheduleAdherence(adminId, resolvedFrom, resolvedTo, agentId));
    }

    /**
     * Si no se pasan fechas, por defecto devuelve el dia de hoy (desde 00:00 hasta ahora).
     */
    private OffsetDateTime[] resolveRange(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime resolvedFrom = from != null ? from : now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime resolvedTo = to != null ? to : now;
        return new OffsetDateTime[]{resolvedFrom, resolvedTo};
    }
}
