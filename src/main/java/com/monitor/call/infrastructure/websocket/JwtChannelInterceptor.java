package com.monitor.call.infrastructure.websocket;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Intercepta el STOMP CONNECT, valida el JWT y registra la sesión directamente
 * en WebSocketPresenceService.
 *
 * Nota: WebSocketPresenceService se inyecta con @Lazy para evitar la dependencia
 * circular WebSocketConfig → JwtChannelInterceptor → WebSocketPresenceService
 *                          → SimpMessagingTemplate → (infraestructura WS de Spring).
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtChannelInterceptor.class);

    private final JwtUtil jwtUtil;
    private final UserJpaRepository userRepo;

    /** @Lazy rompe la dependencia circular con SimpMessagingTemplate */
    @Autowired @Lazy
    private WebSocketPresenceService presenceService;

    public JwtChannelInterceptor(JwtUtil jwtUtil, UserJpaRepository userRepo) {
        this.jwtUtil  = jwtUtil;
        this.userRepo = userRepo;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String auth = accessor.getFirstNativeHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return message;

        String token = auth.substring(7);
        try {
            if (!jwtUtil.isTokenValid(token)) {
                logger.warn("STOMP CONNECT con JWT inválido — sesión sin identidad");
                return message;
            }

            Long userId      = jwtUtil.extractUserId(token);
            String email     = jwtUtil.extractEmail(token);
            Set<Role> roles  = jwtUtil.extractRoles(token);
            String name      = userRepo.findById(userId)
                    .map(u -> u.getName())
                    .orElse(email);
            String sessionId = accessor.getSessionId();

            if (sessionId != null) {
                // Registro directo: evitamos depender de SessionConnectedEvent
                // cuyo mensaje (frame CONNECTED) no expone los atributos del CONNECT
                presenceService.registerSession(sessionId, userId, name, email, roles);
            }

        } catch (Exception e) {
            logger.warn("Error procesando JWT en STOMP CONNECT: {}", e.getMessage());
        }

        return message;
    }
}
