package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.responses.AdminSummaryResponse;
import com.monitor.call.domain.responses.LicenseResponse;
import com.monitor.call.domain.responses.SuperAdminStatsResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.requests.CreateAdminWithLicenseRequest;
import com.monitor.call.infrastructure.requests.UpdateLicenseRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/superadmin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin", description = "Gestión de la plataforma: admins, licencias y estadísticas")
public class SuperAdminController {

    private final UserJpaRepository userRepo;
    private final AgentJpaRepository agentRepo;
    private final LicenseJpaRepository licenseRepo;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminController(UserJpaRepository userRepo,
                                AgentJpaRepository agentRepo,
                                LicenseJpaRepository licenseRepo,
                                PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
        this.licenseRepo = licenseRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/stats")
    @Operation(summary = "Estadísticas globales de la plataforma")
    public ResponseEntity<SuperAdminStatsResponse> getStats() {
        List<UserEntity> admins = userRepo.findByRole(Role.ADMIN);
        long totalAgents = agentRepo.count();
        List<LicenseEntity> licenses = licenseRepo.findAll();

        SuperAdminStatsResponse stats = SuperAdminStatsResponse.builder()
                .totalAdmins(admins.size())
                .activeAdmins(admins.stream().filter(u -> Boolean.TRUE.equals(u.getActive())).count())
                .totalAgents(totalAgents)
                .activeLicenses(licenses.stream().filter(l -> l.getStatus() == LicenseStatus.ACTIVE).count())
                .expiredLicenses(licenses.stream().filter(l -> l.getStatus() == LicenseStatus.EXPIRED).count())
                .trialLicenses(licenses.stream().filter(l -> l.getStatus() == LicenseStatus.TRIAL).count())
                .build();

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/admins")
    @Operation(summary = "Lista todos los admins con su licencia y conteo de agentes")
    public ResponseEntity<List<AdminSummaryResponse>> listAdmins() {
        List<UserEntity> admins = userRepo.findByRole(Role.ADMIN);

        List<AdminSummaryResponse> result = admins.stream().map(admin -> {
            LicenseEntity lic = licenseRepo.findByAdminId(admin.getId()).orElse(null);
            int agentCount = agentRepo.findByAdminId(admin.getId()).size();

            return AdminSummaryResponse.builder()
                    .adminId(admin.getId())
                    .name(admin.getName())
                    .email(admin.getEmail())
                    .active(admin.getActive())
                    .agentCount(agentCount)
                    .license(lic != null ? toResponse(lic) : null)
                    .createdAt(admin.getCreatedAt())
                    .build();
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/admins")
    @Operation(summary = "Crea un nuevo admin con su licencia inicial")
    public ResponseEntity<AdminSummaryResponse> createAdmin(@RequestBody CreateAdminWithLicenseRequest req) {
        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        UserEntity admin = userRepo.save(UserEntity.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .active(true)
                .roles(Set.of(Role.ADMIN))
                .mustChangePassword(true)
                .build());

        LicenseStatus initialStatus = req.getStatus() != null ? req.getStatus() : LicenseStatus.TRIAL;
        LicenseEntity license = licenseRepo.save(LicenseEntity.builder()
                .adminId(admin.getId())
                .planName(req.getPlanName() != null ? req.getPlanName() : "Básico")
                .maxAgents(req.getMaxAgents() != null ? req.getMaxAgents() : 5)
                .status(initialStatus)
                .billingCycle(req.getBillingCycle() != null ? req.getBillingCycle() : com.monitor.call.domain.enums.BillingCycle.MONTHLY)
                .priceMonthly(req.getPriceMonthly())
                .startDate(OffsetDateTime.now())
                .expirationDate(req.getExpirationDate())
                .notes(req.getNotes())
                .build());

        AdminSummaryResponse response = AdminSummaryResponse.builder()
                .adminId(admin.getId())
                .name(admin.getName())
                .email(admin.getEmail())
                .active(admin.getActive())
                .agentCount(0)
                .license(toResponse(license))
                .createdAt(admin.getCreatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    @GetMapping("/licenses")
    @Operation(summary = "Lista todas las licencias")
    public ResponseEntity<List<LicenseResponse>> listLicenses() {
        List<LicenseResponse> licenses = licenseRepo.findAll().stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(licenses);
    }

    @PutMapping("/licenses/{licenseId}")
    @Operation(summary = "Actualiza datos de una licencia")
    public ResponseEntity<LicenseResponse> updateLicense(@PathVariable Long licenseId,
                                                          @RequestBody UpdateLicenseRequest req) {
        LicenseEntity license = licenseRepo.findById(licenseId)
                .orElseThrow(() -> new RuntimeException("Licencia no encontrada"));

        if (req.getPlanName() != null)       license.setPlanName(req.getPlanName());
        if (req.getMaxAgents() != null)      license.setMaxAgents(req.getMaxAgents());
        if (req.getStatus() != null)         license.setStatus(req.getStatus());
        if (req.getBillingCycle() != null)   license.setBillingCycle(req.getBillingCycle());
        if (req.getPriceMonthly() != null)   license.setPriceMonthly(req.getPriceMonthly());
        if (req.getExpirationDate() != null) license.setExpirationDate(req.getExpirationDate());
        if (req.getNotes() != null)          license.setNotes(req.getNotes());

        return ResponseEntity.ok(toResponse(licenseRepo.save(license)));
    }

    private LicenseResponse toResponse(LicenseEntity e) {
        return LicenseResponse.builder()
                .id(e.getId())
                .adminId(e.getAdminId())
                .planName(e.getPlanName())
                .maxAgents(e.getMaxAgents())
                .status(e.getStatus())
                .billingCycle(e.getBillingCycle())
                .priceMonthly(e.getPriceMonthly())
                .startDate(e.getStartDate())
                .expirationDate(e.getExpirationDate())
                .notes(e.getNotes())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
