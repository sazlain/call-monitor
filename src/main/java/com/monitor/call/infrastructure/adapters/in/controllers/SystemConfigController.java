package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.responses.SystemConfigResponse;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@Tag(name = "Configuracion", description = "Configuracion del sistema por administrador")
public class SystemConfigController {

    private final SystemConfigUseCases configUseCases;
    private final JwtUtil jwtUtil;

    public SystemConfigController(SystemConfigUseCases configUseCases, JwtUtil jwtUtil) {
        this.configUseCases = configUseCases;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todos los parametros de configuracion del admin")
    public ResponseEntity<List<SystemConfigResponse>> list(
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractAdminId(auth);
        return ResponseEntity.ok(configUseCases.listByAdmin(adminId));
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener un parametro de configuracion por clave")
    public ResponseEntity<SystemConfigResponse> getByKey(
            @PathVariable String key,
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractAdminId(auth);
        return ResponseEntity.ok(configUseCases.getByKey(adminId, key));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear o actualizar un parametro de configuracion")
    public ResponseEntity<SystemConfigResponse> upsert(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractAdminId(auth);
        String value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(configUseCases.upsert(adminId, key, value));
    }

    @PostMapping("/seed")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Inicializar configuracion por defecto (idempotente)")
    public ResponseEntity<List<SystemConfigResponse>> seed(
            @RequestHeader("Authorization") String auth) {
        Long adminId = extractAdminId(auth);
        configUseCases.seedDefaults(adminId);
        return ResponseEntity.ok(configUseCases.listByAdmin(adminId));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Long extractAdminId(String auth) {
        return jwtUtil.extractUserId(auth.substring(7));
    }
}
