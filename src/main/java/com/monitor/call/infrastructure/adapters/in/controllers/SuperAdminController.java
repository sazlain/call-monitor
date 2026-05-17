package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.responses.AdminSummaryResponse;
import com.monitor.call.domain.responses.AdminTeamResponse;
import com.monitor.call.domain.responses.UserPresenceInfo;
import com.monitor.call.infrastructure.websocket.WebSocketPresenceService;
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
import java.math.RoundingMode;
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
    private final WebSocketPresenceService presenceService;

    public SuperAdminController(UserJpaRepository userRepo,
                                AgentJpaRepository agentRepo,
                                LicenseJpaRepository licenseRepo,
                                LicensePlanJpaRepository planRepo,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                WebSocketPresenceService presenceService) {
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
        this.licenseRepo = licenseRepo;
        this.planRepo = planRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.presenceService = presenceService;
    }

    /**
     * Calcula el precio mensual total de una licencia:
     *   baseFee + (pricePerCallAgent × callAgents) + (pricePerSalesAgent × salesAgents)
     */
    private BigDecimal calcMonthlyPrice(LicensePlanEntity plan, int callAgents, int salesAgents) {
        BigDecimal base       = plan.getPrice()             != null ? plan.getPrice()             : BigDecimal.ZERO;
        BigDecimal perCall    = plan.getPricePerCallAgent()  != null ? plan.getPricePerCallAgent()  : BigDecimal.ZERO;
        BigDecimal perSales   = plan.getPricePerSalesAgent() != null ? plan.getPricePerSalesAgent() : BigDecimal.ZERO;
        return base
                .add(perCall.multiply(BigDecimal.valueOf(callAgents)))
                .add(perSales.multiply(BigDecimal.valueOf(salesAgents)))
                .setScale(2, RoundingMode.HALF_UP);
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

    // ── Presencia en tiempo real ──────────────────────────────────────────────

    @GetMapping("/presence")
    @Operation(summary = "Snapshot de usuarios conectados vía WebSocket en este momento")
    public ResponseEntity<WebSocketPresenceService.PresenceBroadcast> getPresence() {
        List<UserPresenceInfo> users = presenceService.getConnectedUsers();
        return ResponseEntity.ok(new WebSocketPresenceService.PresenceBroadcast(users, users.size()));
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
                    .maxCallAgents(lic != null ? lic.getMaxCallAgents() : null)
                    .maxSalesAgents(lic != null ? lic.getMaxSalesAgents() : null)
                    .priceMonthly(lic != null ? lic.getPriceMonthly() : null)
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
        if (userRepo.existsByEmail(req.getEmail()))
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

        // La tarifa base ya incluye 1 admin + 1 call agent.
        // El campo maxCallAgents del request representa los call agents ADICIONALES.
        int additionalCallAgents  = req.getMaxCallAgents()  != null ? req.getMaxCallAgents()  : (plan.getDefaultMaxCallAgents()  != null ? plan.getDefaultMaxCallAgents()  : 0);
        int additionalSalesAgents = req.getMaxSalesAgents() != null ? req.getMaxSalesAgents() : (plan.getDefaultMaxSalesAgents() != null ? plan.getDefaultMaxSalesAgents() : 0);

        // Total almacenado en la licencia: 1 base + adicionales
        int maxCallAgents  = 1 + additionalCallAgents;
        int maxSalesAgents = additionalSalesAgents;
        int maxAgents      = req.getMaxAgents() != null ? req.getMaxAgents() : plan.getDefaultMaxAgents();

        // El precio se calcula solo sobre los adicionales (la base ya cubre 1 call agent)
        BigDecimal priceMonthly = calcMonthlyPrice(plan, additionalCallAgents, maxSalesAgents);

        LicenseEntity license = licenseRepo.save(LicenseEntity.builder()
                .adminId(admin.getId())
                .planId(plan.getId())
                .planName(plan.getName())
                .maxAgents(maxAgents)
                .maxCallAgents(maxCallAgents)
                .maxSalesAgents(maxSalesAgents)
                .status(LicenseStatus.PENDING)
                .billingCycle(plan.getBillingCycle())
                .priceMonthly(priceMonthly)
                .notes(req.getNotes())
                .build());

        // Enviar email de bienvenida con credenciales, plan contratado e instrucciones de pago
        try {
            String subject = "🎉 Bienvenido a ZentCall — Tu cuenta ha sido creada";
            String priceStr = priceMonthly != null
                    ? String.format("$ %,.0f / mes", priceMonthly.doubleValue()).replace(",", ".")
                    : "—";
            String body = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;color:#333;'>"
                    + "<div style='background:linear-gradient(135deg,#4f46e5,#7c3aed);padding:32px 24px;border-radius:12px 12px 0 0;text-align:center;'>"
                    + "<h1 style='color:#fff;margin:0;font-size:1.6em;'>¡Bienvenido a ZentCall!</h1>"
                    + "<p style='color:#c7d2fe;margin:8px 0 0;'>Tu cuenta de administrador está lista</p>"
                    + "</div>"
                    + "<div style='background:#fff;padding:28px 24px;border:1px solid #e5e7eb;border-top:none;'>"
                    + "<p style='font-size:1.05em;'>Hola, <strong>" + admin.getName() + "</strong>.</p>"
                    + "<p>Tu cuenta de administrador en <strong>ZentCall</strong> ha sido creada. "
                    + "A continuación encontrarás tus credenciales de acceso y el detalle del plan contratado.</p>"

                    // Credenciales
                    + "<h3 style='color:#4f46e5;border-bottom:2px solid #e0e7ff;padding-bottom:6px;margin-top:24px;'>🔑 Credenciales de acceso</h3>"
                    + "<table style='border-collapse:collapse;width:100%;margin:12px 0;background:#f9fafb;border-radius:8px;overflow:hidden;'>"
                    + "<tr><td style='padding:10px 16px;font-weight:bold;color:#6b7280;width:40%;'>Email</td>"
                    + "<td style='padding:10px 16px;'>" + admin.getEmail() + "</td></tr>"
                    + "<tr style='background:#f3f4f6;'><td style='padding:10px 16px;font-weight:bold;color:#6b7280;'>Contraseña temporal</td>"
                    + "<td style='padding:10px 16px;font-family:monospace;font-size:1.15em;letter-spacing:2px;color:#4f46e5;'>"
                    + tempPassword + "</td></tr>"
                    + "</table>"
                    + "<p style='background:#fff8e1;border-left:4px solid #f59e0b;padding:10px 14px;margin:0;color:#92400e;font-size:0.92em;'>"
                    + "⚠️ <strong>Deberás cambiar esta contraseña</strong> en tu primer inicio de sesión.</p>"

                    // Plan contratado
                    + "<h3 style='color:#4f46e5;border-bottom:2px solid #e0e7ff;padding-bottom:6px;margin-top:28px;'>📋 Plan contratado</h3>"
                    + "<table style='border-collapse:collapse;width:100%;margin:12px 0;background:#f9fafb;border-radius:8px;overflow:hidden;'>"
                    + "<tr><td style='padding:10px 16px;font-weight:bold;color:#6b7280;width:50%;'>Plan</td>"
                    + "<td style='padding:10px 16px;font-weight:600;'>" + plan.getName() + "</td></tr>"
                    + "<tr style='background:#f3f4f6;'><td style='padding:10px 16px;font-weight:bold;color:#6b7280;'>Call Agents</td>"
                    + "<td style='padding:10px 16px;font-weight:600;'>" + maxCallAgents + " usuario" + (maxCallAgents != 1 ? "s" : "") + "</td></tr>"
                    + "<tr><td style='padding:10px 16px;font-weight:bold;color:#6b7280;'>Sales Agents</td>"
                    + "<td style='padding:10px 16px;font-weight:600;'>" + maxSalesAgents + " usuario" + (maxSalesAgents != 1 ? "s" : "") + "</td></tr>"
                    + "<tr style='background:#f3f4f6;'><td style='padding:10px 16px;font-weight:bold;color:#6b7280;'>Precio mensual</td>"
                    + "<td style='padding:10px 16px;font-weight:700;color:#4f46e5;font-size:1.1em;'>" + priceStr + "</td></tr>"
                    + "</table>"

                    // Instrucciones de pago
                    + "<div style='background:#eff6ff;border:1px solid #bfdbfe;border-radius:10px;padding:18px 20px;margin-top:24px;'>"
                    + "<h4 style='margin:0 0 10px;color:#1e40af;'>💳 Pasos para activar tu cuenta</h4>"
                    + "<ol style='margin:0;padding-left:20px;color:#1e3a8a;line-height:1.8;'>"
                    + "<li>Ingresa al sistema con las credenciales de arriba.</li>"
                    + "<li>Cambia tu contraseña temporal cuando el sistema te lo solicite.</li>"
                    + "<li>Serás redirigido automáticamente a la página de <strong>Pagos</strong>.</li>"
                    + "<li>Envía tu comprobante de pago por el monto de <strong>" + priceStr + "</strong>.</li>"
                    + "<li>Una vez verificado el pago, tu cuenta quedará <strong>activa</strong>.</li>"
                    + "</ol>"
                    + "</div>"

                    + "<div style='text-align:center;margin-top:28px;'>"
                    + "<a href='https://zentcall.com' style='background:#4f46e5;color:#fff;padding:12px 32px;"
                    + "border-radius:8px;text-decoration:none;font-weight:bold;font-size:1em;display:inline-block;'>"
                    + "Ingresar a ZentCall →</a>"
                    + "</div>"
                    + "<p style='color:#9ca3af;font-size:0.85em;margin-top:24px;text-align:center;'>"
                    + "Si tienes alguna pregunta, contacta al administrador de la plataforma.</p>"
                    + "</div>"
                    + "</div>";
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
                .maxCallAgents(license.getMaxCallAgents())
                .maxSalesAgents(license.getMaxSalesAgents())
                .priceMonthly(license.getPriceMonthly())
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

        int maxAgents      = req.getMaxAgents()      != null ? req.getMaxAgents()      : plan.getDefaultMaxAgents();
        int maxCallAgents  = req.getMaxCallAgents()  != null ? req.getMaxCallAgents()  : (plan.getDefaultMaxCallAgents()  != null ? plan.getDefaultMaxCallAgents()  : 0);
        int maxSalesAgents = req.getMaxSalesAgents() != null ? req.getMaxSalesAgents() : (plan.getDefaultMaxSalesAgents() != null ? plan.getDefaultMaxSalesAgents() : 0);
        BigDecimal priceMonthly = calcMonthlyPrice(plan, maxCallAgents, maxSalesAgents);

        LicenseEntity license = licenseRepo.save(LicenseEntity.builder()
                .adminId(adminId)
                .planId(plan.getId())
                .planName(plan.getName())
                .maxAgents(maxAgents)
                .maxCallAgents(maxCallAgents)
                .maxSalesAgents(maxSalesAgents)
                .status(LicenseStatus.PENDING)
                .billingCycle(plan.getBillingCycle())
                .priceMonthly(priceMonthly)
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
                .defaultMaxAgents(req.getDefaultMaxAgents() != null ? req.getDefaultMaxAgents() : 0)
                .defaultMaxCallAgents(req.getDefaultMaxCallAgents() != null ? req.getDefaultMaxCallAgents() : 0)
                .defaultMaxSalesAgents(req.getDefaultMaxSalesAgents() != null ? req.getDefaultMaxSalesAgents() : 0)
                .price(req.getPrice() != null ? req.getPrice() : BigDecimal.ZERO)
                .pricePerCallAgent(req.getPricePerCallAgent() != null ? req.getPricePerCallAgent() : BigDecimal.ZERO)
                .pricePerSalesAgent(req.getPricePerSalesAgent() != null ? req.getPricePerSalesAgent() : BigDecimal.ZERO)
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
        if (req.getName() != null)                  plan.setName(req.getName());
        if (req.getDescription() != null)           plan.setDescription(req.getDescription());
        if (req.getDefaultMaxAgents() != null)      plan.setDefaultMaxAgents(req.getDefaultMaxAgents());
        if (req.getDefaultMaxCallAgents() != null)  plan.setDefaultMaxCallAgents(req.getDefaultMaxCallAgents());
        if (req.getDefaultMaxSalesAgents() != null) plan.setDefaultMaxSalesAgents(req.getDefaultMaxSalesAgents());
        if (req.getPrice() != null)                 plan.setPrice(req.getPrice());
        if (req.getPricePerCallAgent() != null)     plan.setPricePerCallAgent(req.getPricePerCallAgent());
        if (req.getPricePerSalesAgent() != null)    plan.setPricePerSalesAgent(req.getPricePerSalesAgent());
        if (req.getBillingCycle() != null)          plan.setBillingCycle(req.getBillingCycle());
        if (req.getDurationDays() != null)          plan.setDurationDays(req.getDurationDays());
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
        if (req.getMaxCallAgents() != null)  license.setMaxCallAgents(req.getMaxCallAgents());
        if (req.getMaxSalesAgents() != null) license.setMaxSalesAgents(req.getMaxSalesAgents());
        if (req.getStatus() != null)         license.setStatus(req.getStatus());
        if (req.getBillingCycle() != null)   license.setBillingCycle(req.getBillingCycle());
        if (req.getExpirationDate() != null) license.setExpirationDate(req.getExpirationDate());
        if (req.getNotes() != null)          license.setNotes(req.getNotes());

        // Recalcular precio si se proveen cantidades y el plan tiene precios por usuario
        boolean quantitiesChanged = req.getMaxCallAgents() != null || req.getMaxSalesAgents() != null;
        if (req.getPriceMonthly() != null) {
            // Precio manual explícito — tiene prioridad
            license.setPriceMonthly(req.getPriceMonthly());
        } else if (quantitiesChanged && license.getPlanId() != null) {
            planRepo.findById(license.getPlanId()).ifPresent(plan -> {
                int callAgents  = license.getMaxCallAgents()  != null ? license.getMaxCallAgents()  : 0;
                int salesAgents = license.getMaxSalesAgents() != null ? license.getMaxSalesAgents() : 0;
                license.setPriceMonthly(calcMonthlyPrice(plan, callAgents, salesAgents));
            });
        }

        return ResponseEntity.ok(toLicenseResponse(licenseRepo.save(license)));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private LicensePlanResponse toPlanResponse(LicensePlanEntity e) {
        return LicensePlanResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .defaultMaxAgents(e.getDefaultMaxAgents())
                .defaultMaxCallAgents(e.getDefaultMaxCallAgents())
                .defaultMaxSalesAgents(e.getDefaultMaxSalesAgents())
                .price(e.getPrice())
                .pricePerCallAgent(e.getPricePerCallAgent())
                .pricePerSalesAgent(e.getPricePerSalesAgent())
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
                .maxCallAgents(e.getMaxCallAgents())
                .maxSalesAgents(e.getMaxSalesAgents())
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
