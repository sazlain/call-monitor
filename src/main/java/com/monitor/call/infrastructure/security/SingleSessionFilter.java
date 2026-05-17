package com.monitor.call.infrastructure.security;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de sesión única para CALL_AGENT y SALES_AGENT.
 *
 * Cada login genera un UUID (sessionId) que se guarda en la BD y se embebe en el JWT.
 * En cada request, compara el sessionId del JWT con el de la BD.
 * Si no coinciden → el usuario inició sesión en otro dispositivo → 401 SESSION_INVALIDATED.
 *
 * Corre DESPUÉS de JwtFilter (la autenticación ya está validada).
 */
@Component
public class SingleSessionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SingleSessionFilter.class);

    private static final String SESSION_INVALIDATED_BODY =
            "{\"id\":\"SESSION_INVALIDATED\",\"title\":\"Sesión cerrada\"," +
            "\"message\":\"Tu sesión fue iniciada en otro dispositivo. Por seguridad, esta sesión ha sido cerrada.\"}";

    private final JwtUtil           jwtUtil;
    private final UserJpaRepository userRepo;

    public SingleSessionFilter(JwtUtil jwtUtil, UserJpaRepository userRepo) {
        this.jwtUtil  = jwtUtil;
        this.userRepo = userRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // Solo si el token es válido (JwtFilter ya lo verificó, pero comprobamos por seguridad)
        if (!jwtUtil.isTokenValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        // Verificar si el token tiene sessionId (solo agentes lo tienen)
        String tokenSessionId = jwtUtil.extractSessionId(token);
        if (tokenSessionId == null) {
            // ADMIN / SUPER_ADMIN — sin restricción de sesión única
            chain.doFilter(request, response);
            return;
        }

        // Validar contra la BD
        Long userId = jwtUtil.extractUserId(token);
        UserEntity user = userRepo.findById(userId).orElse(null);

        if (user == null || !tokenSessionId.equals(user.getSessionId())) {
            log.warn("Sesión invalidada para userId={} — otra sesión activa detectada", userId);
            sendSessionInvalidated(response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendSessionInvalidated(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(SESSION_INVALIDATED_BODY);
    }
}
