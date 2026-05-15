package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.PushSubscriptionEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.PushSubscriptionJpaRepository;
import com.monitor.call.infrastructure.requests.PushSubscriptionRequest;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/push")
@Tag(name = "Push Notifications", description = "Suscripción a notificaciones push (PWA)")
public class PushSubscriptionController {

    private final PushSubscriptionJpaRepository pushRepo;
    private final JwtUtil jwtUtil;

    @Value("${vapid.public.key}")
    private String vapidPublicKey;

    public PushSubscriptionController(PushSubscriptionJpaRepository pushRepo, JwtUtil jwtUtil) {
        this.pushRepo = pushRepo;
        this.jwtUtil  = jwtUtil;
    }

    @GetMapping("/vapid-public-key")
    @Operation(summary = "Retorna la clave pública VAPID para que el cliente pueda suscribirse")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", vapidPublicKey));
    }

    @PostMapping("/subscribe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Registra la suscripción push del usuario autenticado")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest req,
                                          @RequestHeader("Authorization") String auth) {
        Long userId = jwtUtil.extractUserId(auth.substring(7));
        pushRepo.findByUserIdAndEndpoint(userId, req.getEndpoint())
                .ifPresentOrElse(
                        existing -> {
                            existing.setP256dh(req.getP256dh());
                            existing.setAuth(req.getAuth());
                            pushRepo.save(existing);
                        },
                        () -> pushRepo.save(PushSubscriptionEntity.builder()
                                .userId(userId)
                                .endpoint(req.getEndpoint())
                                .p256dh(req.getP256dh())
                                .auth(req.getAuth())
                                .build())
                );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/unsubscribe")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Elimina la suscripción push del usuario autenticado")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody PushSubscriptionRequest req,
                                            @RequestHeader("Authorization") String auth) {
        Long userId = jwtUtil.extractUserId(auth.substring(7));
        pushRepo.deleteByUserIdAndEndpoint(userId, req.getEndpoint());
        return ResponseEntity.ok().build();
    }
}
