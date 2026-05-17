package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.ports.in.AuthUseCases;
import com.monitor.call.domain.responses.LoginResponse;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.infrastructure.requests.ChangePasswordRequest;
import com.monitor.call.infrastructure.requests.LoginRequest;
import com.monitor.call.infrastructure.requests.RegisterAdminRequest;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticacion", description = "Login, registro y gestion de usuarios")
public class AuthController {

    private final AuthUseCases authUseCases;
    private final JwtUtil jwtUtil;
    private final SimpMessagingTemplate ws;

    @Value("${app.admin.secret}")
    private String adminSecret;

    public AuthController(AuthUseCases authUseCases, JwtUtil jwtUtil, SimpMessagingTemplate ws) {
        this.authUseCases = authUseCases;
        this.jwtUtil = jwtUtil;
        this.ws = ws;
    }

    @PostMapping("/login")
    @SecurityRequirements({})
    @Operation(summary = "Login para todos los roles. Devuelve JWT con roles embebidos.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authUseCases.login(request.getEmail(), request.getPassword());

        // Notificar en tiempo real al navegador anterior del mismo agente para que cierre sesión
        boolean isAgent = response.getRoles() != null
                && (response.getRoles().contains(Role.CALL_AGENT)
                 || response.getRoles().contains(Role.SALES_AGENT)
                 || response.getRoles().contains(Role.ADMIN));
        if (isAgent && response.getUserId() != null) {
            ws.convertAndSend(
                "/topic/session/" + response.getUserId(),
                "{\"action\":\"SESSION_INVALIDATED\"}"
            );
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    @SecurityRequirements({})
    @Operation(summary = "Registro de administrador. Requiere header X-Admin-Secret.")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterAdminRequest request,
            @RequestHeader("X-Admin-Secret") String secret) {

        if (!adminSecret.equals(secret))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authUseCases.registerAdmin(request.getName(), request.getEmail(), request.getPassword()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Cambiar contrasena. Disponible para cualquier usuario autenticado.")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader("Authorization") String authHeader) {

        Long userId = jwtUtil.extractUserId(authHeader.substring(7));
        authUseCases.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Verifica la sesión activa del usuario autenticado.")
    public ResponseEntity<UserResponse> me(@RequestHeader("Authorization") String authHeader) {
        Long userId = jwtUtil.extractUserId(authHeader.substring(7));
        return ResponseEntity.ok(authUseCases.getUserById(userId));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todos los usuarios activos.")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(authUseCases.listUsers());
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(authUseCases.getUserById(userId));
    }

    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Agregar un rol a un usuario.")
    public ResponseEntity<UserResponse> addRole(@PathVariable Long userId, @RequestParam Role role) {
        return ResponseEntity.ok(authUseCases.addRole(userId, role));
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Quitar un rol a un usuario.")
    public ResponseEntity<UserResponse> removeRole(@PathVariable Long userId, @PathVariable Role role) {
        return ResponseEntity.ok(authUseCases.removeRole(userId, role));
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar un usuario.")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long userId) {
        authUseCases.deactivateUser(userId);
        return ResponseEntity.noContent().build();
    }
}
