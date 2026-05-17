package com.monitor.call.infrastructure.websocket;

import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Intercepta el STOMP CONNECT e inyecta el identity del usuario en los atributos
 * de sesión para que WebSocketPresenceService pueda leerlos en SessionConnectedEvent.
 *
 * Los atributos se prefijan con "ws_" para evitar colisiones.
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(JwtChannelInterceptor.class);

    private final JwtUtil jwtUtil;
    private final UserJpaRepository userRepo;

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
            if (!jwtUtil.isTokenValid(token)) return message;

            Long userId  = jwtUtil.extractUserId(token);
            String email = jwtUtil.extractEmail(token);
            String name  = userRepo.findById(userId)
                    .map(u -> u.getName())
                    .orElse(email);

            Map<String, Object> attrs = accessor.getSessionAttributes();
            if (attrs != null) {
                attrs.put("ws_userId", userId);
                attrs.put("ws_name",   name);
                attrs.put("ws_email",  email);
                attrs.put("ws_roles",  jwtUtil.extractRoles(token));
            }

            logger.debug("STOMP CONNECT autenticado: userId={} name={}", userId, name);
        } catch (Exception e) {
            logger.warn("JWT inválido en STOMP CONNECT: {}", e.getMessage());
        }

        return message;
    }
}
