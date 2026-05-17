package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.SalesAgentUseCases;
import com.monitor.call.domain.responses.SalesAgentResponse;
import com.monitor.call.infrastructure.requests.CreateSalesAgentRequest;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-agents")
@Tag(name = "Sales Agents", description = "Gestión de agentes de ventas")
public class SalesAgentController {

    private final SalesAgentUseCases salesAgentUseCases;
    private final JwtUtil jwtUtil;

    public SalesAgentController(SalesAgentUseCases salesAgentUseCases, JwtUtil jwtUtil) {
        this.salesAgentUseCases = salesAgentUseCases;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un nuevo sales agent")
    public ResponseEntity<SalesAgentResponse> create(
            @Valid @RequestBody CreateSalesAgentRequest request,
            @RequestHeader("Authorization") String auth) {
        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(salesAgentUseCases.createSalesAgent(
                request.getName(), request.getEmail(),
                request.getDefaultCallAgentId(), adminId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar sales agents del admin autenticado")
    public ResponseEntity<List<SalesAgentResponse>> list(
            @RequestHeader("Authorization") String auth) {
        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(salesAgentUseCases.listSalesAgents(adminId));
    }

    @PutMapping("/{id}/assign-agent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar el CALL_AGENT por defecto de un sales agent")
    public ResponseEntity<SalesAgentResponse> assignAgent(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body) {
        Long callAgentId = body.get("agentId");
        return ResponseEntity.ok(salesAgentUseCases.assignCallAgent(id, callAgentId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar un sales agent")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        salesAgentUseCases.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
