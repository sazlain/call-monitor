package com.monitor.call.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final WebhookIpFilter webhookIpFilter;

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    public SecurityConfig(JwtFilter jwtFilter, WebhookIpFilter webhookIpFilter) {
        this.jwtFilter = jwtFilter;
        this.webhookIpFilter = webhookIpFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Publicos
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs",
                        "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs").permitAll()
                // Webhook del proveedor (control de IP via WebhookIpFilter)
                .requestMatchers(HttpMethod.POST, "/api/events/calls/event").permitAll()
                // WebSocket
                .requestMatchers("/ws/**").permitAll()
                // Solo ADMIN
                .requestMatchers("/api/dashboard/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/groups/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                .requestMatchers("/api/reports/**").hasRole("ADMIN")
                // ADMIN + SALES_AGENT
                .requestMatchers(HttpMethod.POST, "/api/leads/**").hasAnyRole("ADMIN", "SALES_AGENT",  "CALL_AGENT")
                .requestMatchers(HttpMethod.PUT, "/api/leads/**").hasAnyRole("ADMIN", "SALES_AGENT")
                .requestMatchers("/api/dashboard/sales/**").hasAnyRole("ADMIN", "SALES_AGENT")
                // ADMIN + CALL_AGENT
                .requestMatchers("/api/dashboard/agent/**").hasAnyRole("ADMIN", "CALL_AGENT")
                .requestMatchers("/api/calls/*/typification").hasAnyRole("ADMIN", "CALL_AGENT")
                .requestMatchers("/api/leads/assigned").hasAnyRole("ADMIN", "CALL_AGENT")
                .requestMatchers("/api/dashboard/status/**").hasAnyRole("ADMIN", "CALL_AGENT")
                    // Citas — todos los roles autenticados
                    .requestMatchers("/api/appointments/**").hasAnyRole("ADMIN", "CALL_AGENT", "SALES_AGENT")
                // Todos los roles autenticados
                .requestMatchers(HttpMethod.GET, "/api/leads/**").hasAnyRole("ADMIN", "SALES_AGENT", "CALL_AGENT")
                .requestMatchers("/api/auth/change-password").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(webhookIpFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
