package com.monitor.call.infrastructure.security;

import com.monitor.call.domain.enums.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${spring.jwt.secret}")
    private String secret;

    @Value("${spring.jwt.token.expiration.minutes}")
    private Long expirationMinutes;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Long userId, String email, Set<Role> roles, String extension) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles.stream().map(Role::name).collect(Collectors.toList()));
        if (extension != null) claims.put("extension", extension);

        return Jwts.builder()
                .claims(claims).subject(email).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000))
                .signWith(getSigningKey()).compact();
    }

    public String extractEmail(String token) { return parseClaims(token).getSubject(); }

    public Long extractUserId(String token) {
        return ((Number) parseClaims(token).get("userId")).longValue();
    }

    @SuppressWarnings("unchecked")
    public Set<Role> extractRoles(String token) {
        List<String> roleNames = (List<String>) parseClaims(token).get("roles");
        return roleNames.stream().map(Role::valueOf).collect(Collectors.toSet());
    }

    public String extractExtension(String token) {
        return (String) parseClaims(token).get("extension");
    }

    public boolean isTokenValid(String token) {
        try { parseClaims(token); return true; }
        catch (JwtException | IllegalArgumentException e) {
            logger.warn("JWT invalido: {}", e.getMessage()); return false;
        }
    }

    public long getExpirationMinutes() { return expirationMinutes; }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload();
    }
}
