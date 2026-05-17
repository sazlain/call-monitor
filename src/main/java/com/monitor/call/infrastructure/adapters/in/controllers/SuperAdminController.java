package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.responses.AdminSummaryResponse;
import com.monitor.call.domain.responses.AdminTeamResponse;
import com.monitor.call.domain.responses.LicensePlanResponse;
import com.monitor.call.domain.responses.LicenseResponse;
import com.monitor.call.domain.responses.SuperAdminStatsResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicensePlanEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicensePlanJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.requests.CreateAdminWithLicenseRequest;
import com.monitor.call.infrastructure.requests.CreatePlanRequest;
import com.monitor.call.infrastructure.requests.UpdateLicenseRequest;
import com.monitor.call.infrastructure.requests.UpdatePlanRequest;
import com.monitor.call.infrastructure.services.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.monitor.call.domain.exceptions.ConflictException;
import com.monitor.call.domain.exceptions.NotFoundException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin", description = "Gestión de la plataforma: admins, planes, licencias y estadísticas")
public class SuperAdminController {

    private static final Logger logger = LoggerFactory.getLogger(SuperAdminController.class);

    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserJpaRepository userRepo;
    private final AgentJpaRepository agentRepo;
    private final LicenseJpaRepository licenseRepo;
    private final LicensePlanJpaRepository planRepo;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public SuperAdminController(UserJpaRepository userRepo,
                                AgentJpaRepository agentRepo,
                                LicenseJpaRepository licenseRepo,
                                LicensePlanJpaRepository planRepo,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
        this.licenseRepo = licenseRepo;
        this.planRepo = planRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    private String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    // ── Estadísticas ──────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @Operation(summary = "Estadísticas globales de la plataforma")
    public ResponseEntity<SuperAdminStatsResponse> getStats() {
        List<UserEntity> admins = userRepo.findByRole(Role.ADMIN);
        List<LicenseEntity> licenses = licenseRepo.findAll();

        BigDecimal mrr = licenses.stream()
                .filter(l -> l.getStatus() == LicenseStatus.ACTIVE && l.getPriceMonthly() != null)
                .map(LicenseEntity::getPriceMonthly)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ResponseEntity.ok(SuperAdminStatsResponse.builder()
                .totalAdmins(admins.size())
                .activeAdmins(admins.stream().filter(u -> Boolean.TRUE.equals(u.getActive())).count())
                .totalAgents(agentRepo.count())
                .pendingLicenses(licenses.stream().filter(l -> l.getStatus() == LicenseStatus.PENDING).count())
                .activeLicenses(licenses.stream().filter(l -> l.getStatus() == LicenseStatus.ACTIVE).count())
                .expiredLicenses(licenses.stream().filter(l -> l.getStatus() == LicenseStatus.EXPIRED).count())
                .suspendedLicenses(licenses.stream().filter(l -> l.getStatus() == LicenseStatus.SUSPENDED).count())
                .mrr(mrr)
                .build());
    }

    // ── Admins ────────────────────────────────────────────────────────────────

    @GetMapping("/admins")
    @Operation(summary = "Lista todos los admins con su plan y estado de licencia")
    public ResponseEntity<List<AdminSummaryResponse>> listAdmins() {
        List<UserEntity> admins = userRepo.findByRole(Role.ADMIN);

        List<AdminSummaryResponse> result = admins.stream().map(admin -> {
            LicenseEntity lic = licenseRepo.findByAdminId(admin.getId()).orElse(null);
            int usedAgents = agentRepo.findByAdminId(admin.getId()).size();

            return AdminSummaryResponse.builder()
                    .id(admin.getId())
                    .name(admin.getName())
                    .email(admin.getEmail())
                    .active(admin.getActive())
                    .usedAgents(usedAgents)
                    .maxAgents(lic != null ? lic.getMaxAgents() : null)
                    .planName(lic != null ? lic.getPlanName() : null)
                    .licenseStatus(lic != null ? lic.getStatus() : null)
                    .licenseId(lic != null ? lic.getId() : null)
                    .activatedAt(lic != null ? lic.getActivatedAt() : null)
                    .expirationDate(lic != null ? lic.getExpirationDate() : null)
                    .createdAt(admin.getCreatedAt())
                    .build();
        }).toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/admins/{adminId}/team")
    @Operation(summary = "Agentes de llamada y sales agents asociados a un admin")
    public ResponseEntity<AdminTeamResponse> getAdminTeam(@PathVariable Long adminId) {
        userRepo.findById(adminId)
                .orElseThrow(() -> new com.monitor.call.domain.exceptions.NotFoundException("Admin no encontrado: " + adminId));

        // Call agents (resueltos por group.adminId)
        List<com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity> agents =
                agentRepo.findByAdminId(adminId);

        List<AdminTeamResponse.CallAgentEntry> callAgents = agents.stream().map(a -> {
            UserEntity user = userRepo.findById(a.getUserId()).orElse(null);
            return AdminTeamResponse.CallAgentEntry.builder()
                    .agentId(a.getId())
                    .userId(a.getUserId())
                    .name(user != null ? user.getName() : "—")
                    .email(user != null ? user.getEmail() : "—")
                    .extension(a.getExtension())
                    .active(a.getActive())
                    .groupName(a.getGroup() != null ? a.getGroup().getName() : null)
                    .build();
        }).toList();

        // Sales agents (resueltos por user.adminId)
        List<UserEntity> salesUsers = userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId);
        List<AdminTeamResponse.SalesAgentEntry> salesAgents = salesUsers.stream().map(sa -> {
            String callAgentName = null;
            if (sa.getDefaultCallAgentId() != null) {
                callAgentName = agentRepo.findById(sa.getDefaultCallAgentId())
                        .flatMap(a -> userRepo.findById(a.getUserId()))
                        .map(UserEntity::getName)
                        .orElse(null);
            }
            return AdminTeamResponse.SalesAgentEntry.builder()
                    .id(sa.getId())
                    .name(sa.getName())
                    .email(sa.getEmail())
                    .active(sa.getActive())
                    .defaultCallAgentName(callAgentName)
                    .build();
        }).toList();

        return ResponseEntity.ok(AdminTeamResponse.builder()
                .callAgents(callAgents)
                .salesAgents(salesAgents)
                .build());
    }

    @PostMapping("/admins")
    @Transactional
    @Operation(summary = "Crea un nuevo admin y le asigna un plan (licencia en estado PENDIENTE)")
    public ResponseEntity<AdminSummaryResponse> createAdmin(@RequestBody CreateAdminWithLicenseRequest req) {
        if (userRepo.findByEmail(req.getEmail()).isPresent())
            throw new ConflictException("El email " + req.getEmail() + " ya existe en el sistema");

        LicensePlanEntity plan = planRepo.findById(req.getPlanId())
                .orElseThrow(() -> new NotFoundException("Plan no encontrado: " + req.getPlanId()));

        String tempPassword = generateTempPassword();

        UserEntity admin = userRepo.save(UserEntity.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(tempPassword))
                .active(true)
                .roles(Set.of(Role.ADMIN))
                .mustChangePassword(true)
                .build());

        int maxAgents = req.getMaxAgents() != null ? req.getMaxAgents() : plan.getDefaultMaxAgents();

        LicenseEntity license = licenseRepo.save(LicenseEntity.builder()
                .adminId(admin.getId())
                .planId(plan.getId())
                .planName(plan.getName())
                .maxAgents(maxAgents)
                .status(LicenseStatus.PENDING)
                .billingCycle(plan.getBillingCycle())
                .priceMonthly(plan.getPrice())
                .notes(req.getNotes())
                .build());

        // Enviar email de bienvenida con la clave temporal
        try {
            String subject = "🎉 Bienvenido a ZentCall — Tu cuenta ha sido creada";
            String body = "<h2>¡Hola, " + admin.getName() + "!</h2>"
                    + "<p>Tu cuenta de administrador en <strong>ZentCall</strong> ha sido creada exitosamente.</p>"
                    + "<p>Usa las siguientes credenciales para tu primer inicio de sesión:</p>"
                    + "<table style='border-collapse:collapse;margin:16px 0;'>"
                    + "<tr><td style='padding:6px 12px;font-weight:bold;'>Email:</td>"
                    + "<td style='padding:6px 12px;'>" + admin.getEmail() + "</td></tr>"
                    + "<tr><td style='padding:6px 12px;font-weight:bold;'>Contraseña temporal:</td>"
                    + "<td style='padding:6px 12px;font-family:monospace;font-size:1.1em;'>" + tempPassword + "</td></tr>"
                    + "</table>"
                    + "<p style='color:#e65c00;'><strong>⚠️ Deberás cambiar esta contraseña en tu primer inicio de sesión.</strong></p>"
                    + "<p>Accede al sistema en: <a href='https://zentcall.com'>zentcall.com</a></p>"
                    + "<p>Si tienes alguna pregunta, contacta al administrador de la plataforma.</p>";
            emailService.send(admin.getEmail(), subject, body);
        } catch (Exception e) {
            logger.warn("No se pudo enviar email de bienvenida al admin {}: {}", admin.getEmail(), e.getMessage());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(AdminSummaryResponse.builder()
                .id(admin.getId())
                .name(admin.getName())
                .email(admin.getEmail())
                .active(admin.getActive())
                .usedAgents(0)
                .maxAgents(license.getMaxAgents())
                .planName(plan.getName())
                .licenseStatus(LicenseStatus.PENDING)
                .licenseId(license.getId())
                .createdAt(admin.getCreatedAt())
                .build());
    }

    @PostMapping("/admins/{adminId}/license")
    @Operation(summary = "Asigna una licencia a un admin que no tiene ninguna")
    public ResponseEntity<LicenseResponse> assignLicense(
            @PathVariable Long adminId,
            @RequestBody CreateAdminWithLicenseRequest req) {

        userRepo.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Admin no encontrado: " + adminId));

        if (licenseRepo.findByAdminId(adminId).isPresent())
            throw new ConflictException("Este admin ya tiene una licencia asignada");

        LicensePlanEntity plan = planRepo.findById(req.getPlanId())
                .orElseThrow(() -> new NotFoundException("Plan no encontrado: " + req.getPlanId()));

        int maxAgents = req.getMaxAgents() != null ? req.getMaxAgents() : plan.getDefaultMaxAgents();

        LicenseEntity license = licenseRepo.save(LicenseEntity.builder()
                .adminId(adminId)
                .planId(plan.getId())
                .planName(plan.getName())
                .maxAgents(maxAgents)
                .status(LicenseStatus.PENDING)
                .billingCycle(plan.getBillingCycle())
                .priceMonthly(plan.getPrice())
                .notes(req.getNotes())
                .build());

        return ResponseEntity.ok(toLicenseResponse(license));
    }

    @PutMapping("/admins/{adminId}/toggle-active")
    @Operation(summary = "Activa o desactiva la cuenta de un admin")
    public ResponseEntity<Void> toggleActive(@PathVariable Long adminId) {
        userRepo.findById(adminId).ifPresent(u -> {
            u.setActive(!Boolean.TRUE.equals(u.getActive()));
            userRepo.save(u);
        });
        return ResponseEntity.ok().build();
    }

    // ── Planes ────────────────────────────────────────────────────────────────

    @GetMapping("/plans")
    @Operation(summary = "Lista los planes activos (para selectores)")
    public ResponseEntity<List<LicensePlanResponse>> listActivePlans() {
        return ResponseEntity.ok(planRepo.findByActiveTrue().stream()
                .map(this::toPlanResponse).toList());
    }

    @GetMapping("/plans/all")
    @Operation(summary = "Lista todos los planes incluyendo inactivos (gestión)")
    public ResponseEntity<List<LicensePlanResponse>> listAllPlans() {
        return ResponseEntity.ok(planRepo.findAll().stream()
                .map(this::toPlanResponse).toList());
    }

    @PostMapping("/plans")
    @Operation(summary = "Crea un nuevo plan de licencia")
    public ResponseEntity<LicensePlanResponse> createPlan(@RequestBody CreatePlanRequest req) {
        LicensePlanEntity plan = planRepo.save(LicensePlanEntity.builder()
                .name(req.getName())
                .description(req.getDescription())
                .defaultMaxAgents(req.getDefaultMaxAgents())
                .price(req.getPrice())
                .billingCycle(req.getBillingCycle())
                .durationDays(req.getDurationDays())
                .active(true)
                .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(toPlanResponse(plan));
    }

    @PutMapping("/plans/{planId}")
    @Operation(summary = "Actualiza un plan de licencia")
    public ResponseEntity<LicensePlanResponse> updatePlan(@PathVariable Long planId,
                                                           @RequestBody UpdatePlanRequest req) {
        LicensePlanEntity plan = planRepo.findById(planId)
                .orElseThrow(() -> new NotFoundException("Plan no encontrado: " + planId));
        if (req.getName() != null)             plan.setName(req.getName());
        if (req.getDescription() != null)      plan.setDescription(req.getDescription());
        if (req.getDefaultMaxAgents() != null) plan.setDefaultMaxAgents(req.getDefaultMaxAgents());
        if (req.getPrice() != null)            plan.setPrice(req.getPrice());
        if (req.getBillingCycle() != null)     plan.setBillingCycle(req.getBillingCycle());
        if (req.getDurationDays() != null)     plan.setDurationDays(req.getDurationDays());
        return ResponseEntity.ok(toPlanResponse(planRepo.save(plan)));
    }

    @DeleteMapping("/plans/{planId}")
    @Operation(summary = "Desactiva un plan (borrado lógico, nunca se elimina físicamente)")
    public ResponseEntity<Void> deletePlan(@PathVariable Long planId) {
        LicensePlanEntity plan = planRepo.findById(planId)
                .orElseThrow(() -> new NotFoundException("Plan no encontrado: " + planId));
        plan.setActive(false);
        planRepo.save(plan);
        return ResponseEntity.noContent().build();
    }

    // ── Licencias ─────────────────────────────────────────────────────────────

    @GetMapping("/licenses")
    @Operation(summary = "Lista todas las licencias")
    public ResponseEntity<List<LicenseResponse>> listLicenses() {
        return ResponseEntity.ok(licenseRepo.findAll().stream().map(this::toLicenseResponse).toList());
    }

    @PostMapping("/licenses/{licenseId}/activate")
    @Transactional
    @Operation(summary = "Activa una licencia: registra fecha de inicio hoy y calcula vencimiento según el plan")
    public ResponseEntity<LicenseResponse> activateLicense(@PathVariable Long licenseId) {
        LicenseEntity license = licenseRepo.findById(licenseId)
                .orElseThrow(() -> new NotFoundException("Licencia no encontrada"));

        LicensePlanEntity plan = license.getPlanId() != null
                ? planRepo.findById(license.getPlanId()).orElse(null)
                : null;

        OffsetDateTime now = OffsetDateTime.now();
        int durationDays = (plan != null) ? plan.getDurationDays() : 30;

        license.setStatus(LicenseStatus.ACTIVE);
        license.setActivatedAt(now);
        license.setStartDate(now);
        license.setExpirationDate(now.plusDays(durationDays));

        return ResponseEntity.ok(toLicenseResponse(licenseRepo.save(license)));
    }

    @PutMapping("/licenses/{licenseId}")
    @Operation(summary = "Actualiza datos de una licencia")
    public ResponseEntity<LicenseResponse> updateLicense(@PathVariable Long licenseId,
                                                          @RequestBody UpdateLicenseRequest req) {
        LicenseEntity license = licenseRepo.findById(licenseId)
                .orElseThrow(() -> new NotFoundException("Licencia no encontrada"));

        if (req.getPlanName() != null)       license.setPlanName(req.getPlanName());
        if (req.getMaxAgents() != null)      license.setMaxAgents(req.getMaxAgents());
        if (req.getStatus() != null)         license.setStatus(req.getStatus());
        if (req.getBillingCycle() != null)   license.setBillingCycle(req.getBillingCycle());
        if (req.getPriceMonthly() != null)   license.setPriceMonthly(req.getPriceMonthly());
        if (req.getExpirationDate() != null) license.setExpirationDate(req.getExpirationDate());
        if (req.getNotes() != null)          license.setNotes(req.getNotes());

        return ResponseEntity.ok(toLicenseResponse(licenseRepo.save(license)));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private LicensePlanResponse toPlanResponse(LicensePlanEntity e) {
        return LicensePlanResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .defaultMaxAgents(e.getDefaultMaxAgents())
                .price(e.getPrice())
                .billingCycle(e.getBillingCycle())
                .durationDays(e.getDurationDays())
                .active(e.getActive())
                .licenseCount(licenseRepo.countByPlanId(e.getId()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private LicenseResponse toLicenseResponse(LicenseEntity e) {
        return LicenseResponse.builder()
                .id(e.getId())
                .adminId(e.getAdminId())
                .planId(e.getPlanId())
                .planName(e.getPlanName())
                .maxAgents(e.getMaxAgents())
                .status(e.getStatus())
                .billingCycle(e.getBillingCycle())
                .priceMonthly(e.getPriceMonthly())
                .activatedAt(e.getActivatedAt())
                .startDate(e.getStartDate())
                .expirationDate(e.getExpirationDate())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
