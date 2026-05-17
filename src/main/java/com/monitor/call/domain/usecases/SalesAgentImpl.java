package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.ports.in.SalesAgentUseCases;
import com.monitor.call.domain.responses.SalesAgentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.services.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SalesAgentImpl implements SalesAgentUseCases {

    private static final Logger logger = LoggerFactory.getLogger(SalesAgentImpl.class);

    private final UserJpaRepository userRepo;
    private final AgentJpaRepository agentRepo;
    private final LeadJpaRepository leadRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public SalesAgentImpl(UserJpaRepository userRepo,
                          AgentJpaRepository agentRepo,
                          LeadJpaRepository leadRepo,
                          PasswordEncoder passwordEncoder,
                          EmailService emailService) {
        this.userRepo        = userRepo;
        this.agentRepo       = agentRepo;
        this.leadRepo        = leadRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService    = emailService;
    }

    @Override
    @Transactional
    public SalesAgentResponse createSalesAgent(String name, String email,
                                               Long defaultCallAgentId, Long adminId) {
        if (userRepo.existsByEmail(email)) {
            throw new RuntimeException("Ya existe un usuario con el email: " + email);
        }

        String tempPassword = UUID.randomUUID().toString().substring(0, 10);

        UserEntity user = UserEntity.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(tempPassword))
                .roles(Set.of(Role.SALES_AGENT))
                .active(true)
                .mustChangePassword(true)
                .adminId(adminId)
                .defaultCallAgentId(defaultCallAgentId)
                .build();

        user = userRepo.save(user);

        // Email de bienvenida
        try {
            String agentName = defaultCallAgentId != null
                    ? agentRepo.findById(defaultCallAgentId)
                              .flatMap(a -> userRepo.findById(a.getUserId()))
                              .map(UserEntity::getName).orElse("—")
                    : null;

            String body = "<h2>Bienvenido a ZentCall</h2>"
                    + "<p>Tu cuenta de Sales Agent ha sido creada.</p>"
                    + "<table style='border-collapse:collapse;'>"
                    + "<tr><td style='padding:4px 8px;color:#6b7280;'>Email:</td>"
                    + "<td style='padding:4px 8px;font-weight:500;'>" + email + "</td></tr>"
                    + "<tr><td style='padding:4px 8px;color:#6b7280;'>Contraseña temporal:</td>"
                    + "<td style='padding:4px 8px;font-weight:500;'>" + tempPassword + "</td></tr>"
                    + (agentName != null
                        ? "<tr><td style='padding:4px 8px;color:#6b7280;'>Agente asignado:</td>"
                          + "<td style='padding:4px 8px;font-weight:500;'>" + agentName + "</td></tr>"
                        : "")
                    + "</table>"
                    + "<p style='color:#ef4444;'>Debes cambiar tu contraseña al iniciar sesión.</p>";
            emailService.send(email, "Bienvenido a ZentCall — credenciales de acceso", body);
        } catch (Exception e) {
            logger.warn("No se pudo enviar email de bienvenida al sales agent {}: {}", email, e.getMessage());
        }

        logger.info("Sales agent creado: id={} email={} adminId={}", user.getId(), email, adminId);
        return toResponse(user);
    }

    @Override
    public List<SalesAgentResponse> listSalesAgents(Long adminId) {
        return userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public SalesAgentResponse assignCallAgent(Long salesAgentId, Long callAgentId) {
        UserEntity user = userRepo.findById(salesAgentId)
                .orElseThrow(() -> new RuntimeException("Sales agent no encontrado: " + salesAgentId));
        user.setDefaultCallAgentId(callAgentId);
        return toResponse(userRepo.save(user));
    }

    @Override
    @Transactional
    public void deactivate(Long salesAgentId) {
        UserEntity user = userRepo.findById(salesAgentId)
                .orElseThrow(() -> new RuntimeException("Sales agent no encontrado: " + salesAgentId));
        user.setActive(false);
        userRepo.save(user);
        logger.info("Sales agent desactivado: id={}", salesAgentId);
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private SalesAgentResponse toResponse(UserEntity u) {
        String defaultCallAgentName = null;
        if (u.getDefaultCallAgentId() != null) {
            defaultCallAgentName = agentRepo.findById(u.getDefaultCallAgentId())
                    .flatMap(a -> userRepo.findById(a.getUserId()))
                    .map(UserEntity::getName)
                    .orElse(null);
        }
        int leadCount = leadRepo.findByOwnerId(u.getId()).size();

        return SalesAgentResponse.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .active(u.getActive())
                .adminId(u.getAdminId())
                .defaultCallAgentId(u.getDefaultCallAgentId())
                .defaultCallAgentName(defaultCallAgentName)
                .leadCount(leadCount)
                .build();
    }
}
