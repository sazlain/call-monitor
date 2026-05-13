package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import com.monitor.call.domain.ports.in.AgentGoalUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.responses.AgentGoalHistoryResponse;
import com.monitor.call.domain.responses.AgentGoalResponse;
import com.monitor.call.domain.responses.GoalProgressResponse;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Metas", description = "Gestion de metas por KPI para agentes")
public class AgentGoalController {

    private final AgentGoalUseCases goalUseCases;
    private final AgentRepositoryPort agentRepo;
    private final JwtUtil jwtUtil;

    public AgentGoalController(AgentGoalUseCases goalUseCases,
                                AgentRepositoryPort agentRepo,
                                JwtUtil jwtUtil) {
        this.goalUseCases = goalUseCases;
        this.agentRepo = agentRepo;
        this.jwtUtil = jwtUtil;
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @PostMapping("/api/admin/goals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear una meta de KPI para un agente")
    public ResponseEntity<AgentGoalResponse> create(
            @RequestBody CreateGoalRequest req,
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractUserId(auth);
        AgentGoalResponse response = goalUseCases.createGoal(
                adminId, req.agentId(), req.kpiType(), req.period(), req.targetValue());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/admin/goals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar metas activas del admin, opcionalmente por agente")
    public ResponseEntity<List<AgentGoalResponse>> list(
            @RequestParam(required = false) Long agentId,
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractUserId(auth);
        return ResponseEntity.ok(goalUseCases.listGoals(adminId, agentId));
    }

    @PutMapping("/api/admin/goals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar el valor objetivo de una meta")
    public ResponseEntity<AgentGoalResponse> update(
            @PathVariable Long id,
            @RequestBody Map<String, Double> body,
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractUserId(auth);
        Double target = body.get("targetValue");
        if (target == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(goalUseCases.updateGoal(id, target, adminId));
    }

    @DeleteMapping("/api/admin/goals/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar una meta")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractUserId(auth);
        goalUseCases.deactivateGoal(id, adminId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/admin/goals/history")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historial de metas evaluadas, filtros opcionales")
    public ResponseEntity<List<AgentGoalHistoryResponse>> history(
            @RequestParam(required = false) Long agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractUserId(auth);
        return ResponseEntity.ok(goalUseCases.getHistory(adminId, agentId, from, to));
    }

    // ── Agent endpoints ───────────────────────────────────────────────────────

    @GetMapping("/api/goals/my")
    @PreAuthorize("hasRole('CALL_AGENT')")
    @Operation(summary = "Ver mis metas con progreso actual")
    public ResponseEntity<List<GoalProgressResponse>> myGoals(
            @RequestHeader("Authorization") String auth) {
        Long userId = extractUserId(auth);
        var agent = agentRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado para userId: " + userId));
        Long adminId = agent.getAdminId();
        if (adminId == null) {
            throw new RuntimeException("El agente no tiene un grupo asignado: " + agent.getId());
        }
        return ResponseEntity.ok(goalUseCases.getMyGoals(agent.getId(), adminId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long extractUserId(String auth) {
        return jwtUtil.extractUserId(auth.substring(7));
    }

    //── Inner request record ──────────────────────────────────────────────────

    public record CreateGoalRequest(
            Long agentId,
            @NotNull GoalKpi kpiType,
            @NotNull GoalPeriod period,
            @NotNull Double targetValue) {}
}
