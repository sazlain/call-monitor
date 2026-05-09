package com.monitor.call.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filtro de IP para el endpoint de webhooks del proveedor de telefonia.
 * Solo aplica a: POST /api/events/calls/event
 * Lee X-Forwarded-For porque siempre hay nginx delante.
 *
 * .env: WEBHOOK_ALLOWED_IPS=190.x.x.x,190.x.x.y
 * Usar * mientras no se tenga la IP del proveedor (modo abierto con log).
 */
@Component
public class WebhookIpFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(WebhookIpFilter.class);
    private static final String WEBHOOK_PATH = "/api/events/calls/event";
    private static final String WILDCARD = "*";

    @Value("${app.webhook.allowed-ips:*}")
    private String allowedIpsConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!WEBHOOK_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = extractRealIp(request);
        List<String> allowedIps = Arrays.stream(allowedIpsConfig.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).collect(Collectors.toList());

        boolean isWildcard = allowedIps.isEmpty() || allowedIps.contains(WILDCARD);

        if (isWildcard) {
            logger.warn("WEBHOOK [MODO ABIERTO] IP: {} | Configura WEBHOOK_ALLOWED_IPS cuando tengas la IP del proveedor", clientIp);
            filterChain.doFilter(request, response);
            return;
        }

        if (allowedIps.contains(clientIp)) {
            logger.debug("WEBHOOK [PERMITIDO] IP: {}", clientIp);
            filterChain.doFilter(request, response);
        } else {
            logger.warn("WEBHOOK [BLOQUEADO] IP no autorizada: {} | Permitidas: {}", clientIp, allowedIpsConfig);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Acceso denegado\",\"message\":\"IP no autorizada\"}");
        }
    }

    private String extractRealIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String ip = xff.split(",")[0].trim();
            logger.debug("IP de X-Forwarded-For: {} (header: {})", ip, xff);
            return ip;
        }
        return request.getRemoteAddr();
    }
}
