package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.AgentGroupUseCases;
import com.monitor.call.domain.responses.AgentGroupResponse;
import com.monitor.call.infrastructure.requests.CreateGroupRequest;
import com.monitor.call.infrastructure.requests.UpdateGroupRequest;
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
@RequestMapping("/api/groups")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Grupos de Agentes", description = "Gestion de grupos — solo ADMIN")
public class AgentGroupController {

    private final AgentGroupUseCases groupUseCases;
    private final JwtUtil jwtUtil;

    public AgentGroupController(AgentGroupUseCases groupUseCases, JwtUtil jwtUtil) {
        this.groupUseCases = groupUseCases;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @Operation(summary = "Crear un grupo de agentes")
    public ResponseEntity<AgentGroupResponse> create(
            @Valid @RequestBody CreateGroupRequest request,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(groupUseCases.createGroup(request.getName(), request.getDescription(), adminId));
    }

    @GetMapping
    @Operation(summary = "Listar mis grupos")
    public ResponseEntity<List<AgentGroupResponse>> list(
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(groupUseCases.listGroupsByAdmin(adminId));
    }

    @GetMapping("/{groupId}")
    @Operation(summary = "Obtener un grupo por ID")
    public ResponseEntity<AgentGroupResponse> get(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(groupUseCases.getGroup(groupId, adminId));
    }

    @PutMapping("/{groupId}")
    @Operation(summary = "Actualizar un grupo")
    public ResponseEntity<AgentGroupResponse> update(
            @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(groupUseCases.updateGroup(
                groupId, request.getName(), request.getDescription(), adminId));
    }

    @DeleteMapping("/{groupId}")
    @Operation(summary = "Desactivar un grupo")
    public ResponseEntity<Void> deactivate(
            @PathVariable Long groupId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        groupUseCases.deactivateGroup(groupId, adminId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{groupId}/agents/{agentId}")
    @Operation(summary = "Asignar un agente a este grupo")
    public ResponseEntity<AgentGroupResponse> assignAgent(
            @PathVariable Long groupId,
            @PathVariable Long agentId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(groupUseCases.assignAgentToGroup(groupId, agentId, adminId));
    }

    @DeleteMapping("/{groupId}/agents/{agentId}")
    @Operation(summary = "Remover un agente del grupo")
    public ResponseEntity<AgentGroupResponse> removeAgent(
            @PathVariable Long groupId,
            @PathVariable Long agentId,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(groupUseCases.removeAgentFromGroup(groupId, agentId, adminId));
    }
}
