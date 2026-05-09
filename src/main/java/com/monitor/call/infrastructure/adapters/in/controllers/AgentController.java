package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.AgentUseCases;
import com.monitor.call.domain.responses.AgentResponse;
import com.monitor.call.infrastructure.requests.CreateAgentRequest;
import com.monitor.call.infrastructure.requests.UpdateAgentRequest;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@Tag(name = "Agentes", description = "Gestion de agentes")
public class AgentController {

    private final AgentUseCases agentUseCases;
    private final JwtUtil jwtUtil;

    public AgentController(AgentUseCases agentUseCases, JwtUtil jwtUtil) {
        this.agentUseCases = agentUseCases;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear agente (crea tambien su usuario con contrasena temporal)")
    public ResponseEntity<AgentResponse> create(
            @Valid @RequestBody CreateAgentRequest request,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.status(HttpStatus.CREATED).body(
                agentUseCases.createAgent(request.getName(), request.getEmail(),
                        request.getExtension(), request.getGroupId(), adminId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar agentes. Filtra por groupId si se provee.")
    public ResponseEntity<List<AgentResponse>> list(
            @RequestParam(required = false) Long groupId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        List<AgentResponse> agents = groupId != null
                ? agentUseCases.listAgentsByGroup(groupId)
                : agentUseCases.listAgentsByAdmin(adminId);
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/{agentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT', 'SALES_AGENT')")
    @Operation(summary = "Obtener agente por ID")
    public ResponseEntity<AgentResponse> get(@PathVariable Long agentId) {
        return ResponseEntity.ok(agentUseCases.getAgent(agentId));
    }

    @GetMapping("/extension/{extension}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT')")
    @Operation(summary = "Obtener agente por extension telefonica")
    public ResponseEntity<AgentResponse> getByExtension(@PathVariable String extension) {
        return ResponseEntity.ok(agentUseCases.getAgentByExtension(extension));
    }

    @PutMapping("/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar nombre y extension de un agente")
    public ResponseEntity<AgentResponse> update(
            @PathVariable Long agentId,
            @Valid @RequestBody UpdateAgentRequest request,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(
                agentUseCases.updateAgent(agentId, request.getName(), request.getExtension(), adminId));
    }

    @DeleteMapping("/{agentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar un agente")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long agentId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        agentUseCases.deactivateAgent(agentId, adminId);
        return ResponseEntity.noContent().build();
    }
}
