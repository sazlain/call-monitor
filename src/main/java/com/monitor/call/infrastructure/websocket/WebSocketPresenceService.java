package com.monitor.call.infrastructure.websocket;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.responses.UserPresenceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Rastrea sesiones WebSocket activas y emite actualizaciones en tiempo real
 * al canal /topic/presence/superadmin.
 *
 * Registro:   JwtChannelInterceptor llama a registerSession() al interceptar CONNECT.
 * Baja:       onDisconnect() escucha SessionDisconnectEvent de Spring.
 *
 * Se evita escuchar SessionConnectedEvent porque ese evento envuelve el frame CONNECTED
 * (respuesta del servidor) y no garantiza acceso a los atributos del frame CONNECT original.
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

    // ── API pública ────────────────────────────────────────────────────────────

    /**
     * Llamado por JwtChannelInterceptor al procesar el STOMP CONNECT.
     * Si la sesión ya existía (reconexión), la sobreescribe.
     */
    public void registerSession(String sessionId, Long userId, String name, String email, Set<Role> roles) {
        sessions.put(sessionId, UserPresenceInfo.builder()
                .sessionId(sessionId)
                .userId(userId)
                .name(name)
                .email(email)
                .roles(roles)
                .connectedAt(OffsetDateTime.now())
                .build());
        logger.info("WS conectado: {} ({}) | session={} | total sesiones={}", name, email, sessionId, sessions.size());
        broadcast();
    }

    /**
     * Lista de usuarios únicos conectados (dedup por userId, se conserva la sesión
     * más antigua), ordenados por connectedAt ascendente.
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

    /** Número real de sesiones abiertas (incluye múltiples pestañas del mismo usuario). */
    public int getTotalSessions() { return sessions.size(); }

    // ── Evento de desconexión ─────────────────────────────────────────────────

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        UserPresenceInfo removed = sessions.remove(sessionId);
        if (removed != null) {
            logger.info("WS desconectado: {} ({}) | session={} | total sesiones={}",
                    removed.getName(), removed.getEmail(), sessionId, sessions.size());
            broadcast();
        }
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private void broadcast() {
        try {
            List<UserPresenceInfo> users = getConnectedUsers();
            messagingTemplate.convertAndSend(PRESENCE_TOPIC, new PresenceBroadcast(users, users.size()));
            logger.debug("Presencia emitida: {} usuario(s) únicos, {} sesiones", users.size(), sessions.size());
        } catch (Exception e) {
            logger.warn("Error emitiendo presencia: {}", e.getMessage());
        }
    }

    /** Wrapper serializable para evitar ambigüedad con Map en convertAndSend */
    public record PresenceBroadcast(List<UserPresenceInfo> users, int count) {}
}
