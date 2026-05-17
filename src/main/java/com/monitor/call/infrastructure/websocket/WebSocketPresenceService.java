package com.monitor.call.infrastructure.websocket;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.responses.UserPresenceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Rastrea qué usuarios tienen sesiones WebSocket activas y emite actualizaciones
 * en tiempo real al canal /topic/presence/superadmin.
 *
 * Flujo:
 *  1. JwtChannelInterceptor extrae userId/name/email/roles del STOMP CONNECT y los
 *     guarda en los atributos de sesión.
 *  2. onConnect lee esos atributos y registra la sesión en el mapa interno.
 *  3. onDisconnect elimina la sesión del mapa.
 *  4. Ambos eventos disparan un broadcast hacia el super admin.
 */
@Service
public class WebSocketPresenceService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketPresenceService.class);
    private static final String PRESENCE_TOPIC = "/topic/presence/superadmin";

    /** sessionId → presencia. Un mismo userId puede tener varias pestañas abiertas. */
    private final ConcurrentHashMap<String, UserPresenceInfo> sessions = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPresenceService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // ── Eventos de ciclo de vida ───────────────────────────────────────────────

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();

        if (sessionId == null || attrs == null) return;

        Long userId = (Long) attrs.get("ws_userId");
        if (userId == null) return; // conexión sin token (ignorar)

        String name   = (String) attrs.getOrDefault("ws_name", "Desconocido");
        String email  = (String) attrs.getOrDefault("ws_email", "");
        @SuppressWarnings("unchecked")
        Set<Role> roles = (Set<Role>) attrs.getOrDefault("ws_roles", Set.of());

        sessions.put(sessionId, UserPresenceInfo.builder()
                .sessionId(sessionId)
                .userId(userId)
                .name(name)
                .email(email)
                .roles(roles)
                .connectedAt(OffsetDateTime.now())
                .build());

        logger.info("WS conectado: {} ({}) | session={} | total={}", name, email, sessionId, sessions.size());
        broadcast();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        UserPresenceInfo removed = sessions.remove(sessionId);
        if (removed != null) {
            logger.info("WS desconectado: {} ({}) | session={} | total={}", removed.getName(), removed.getEmail(), sessionId, sessions.size());
            broadcast();
        }
    }

    // ── API pública ────────────────────────────────────────────────────────────

    /**
     * Lista de usuarios conectados, deduplicados por userId (se conserva la sesión
     * más antigua para cada usuario), ordenados por connectedAt ascendente.
     */
    public List<UserPresenceInfo> getConnectedUsers() {
        return sessions.values().stream()
                .collect(Collectors.groupingBy(
                        UserPresenceInfo::getUserId,
                        Collectors.minBy(Comparator.comparing(UserPresenceInfo::getConnectedAt))
                ))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(UserPresenceInfo::getConnectedAt))
                .toList();
    }

    /** Número total de sesiones abiertas (incluye múltiples pestañas del mismo usuario). */
    public int getTotalSessions() { return sessions.size(); }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private void broadcast() {
        try {
            List<UserPresenceInfo> users = getConnectedUsers();
            PresenceBroadcast payload = new PresenceBroadcast(users, users.size());
            messagingTemplate.convertAndSend(PRESENCE_TOPIC, payload);
        } catch (Exception e) {
            logger.warn("Error emitiendo presencia: {}", e.getMessage());
        }
    }

    /** Wrapper serializable para evitar ambigüedad con Map en convertAndSend */
    public record PresenceBroadcast(List<UserPresenceInfo> users, int count) {}
}
