package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.CallTypificationUseCases;
import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.infrastructure.requests.CallTypificationRequest;
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
@RequestMapping("/api/calls")
@Tag(name = "Tipificacion de Llamadas", description = "Registro del resultado de cada llamada")
public class CallTypificationController {

    private final CallTypificationUseCases typUseCases;
    private final JwtUtil jwtUtil;

    public CallTypificationController(CallTypificationUseCases typUseCases, JwtUtil jwtUtil) {
        this.typUseCases = typUseCases;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/typification")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT')")
    @Operation(summary = "Tipificar una llamada. Se llama al recibir el evento HANGUP.")
    public ResponseEntity<CallTypificationResponse> typify(
            @Valid @RequestBody CallTypificationRequest request,
            @RequestHeader("Authorization") String auth) {

        Long agentId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.status(HttpStatus.CREATED).body(typUseCases.typify(request, agentId));
    }

    @PutMapping("/{callId}/typification")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT')")
    @Operation(summary = "Corregir la tipificacion de una llamada ya tipificada")
    public ResponseEntity<CallTypificationResponse> update(
            @PathVariable String callId,
            @Valid @RequestBody CallTypificationRequest request,
            @RequestHeader("Authorization") String auth) {

        Long agentId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(typUseCases.updateTypification(callId, request, agentId));
    }

    @GetMapping("/{callId}/typification")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT', 'SALES_AGENT')")
    @Operation(summary = "Obtener tipificacion de una llamada")
    public ResponseEntity<CallTypificationResponse> get(@PathVariable String callId) {
        return ResponseEntity.ok(typUseCases.getByCallId(callId));
    }

    @GetMapping("/typifications/lead/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT', 'CALL_AGENT')")
    @Operation(summary = "Historial de tipificaciones de un lead")
    public ResponseEntity<List<CallTypificationResponse>> listByLead(@PathVariable Long leadId) {
        return ResponseEntity.ok(typUseCases.listByLead(leadId));
    }
}
