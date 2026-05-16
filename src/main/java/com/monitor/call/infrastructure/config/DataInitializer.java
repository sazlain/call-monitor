package com.monitor.call.infrastructure.config;

import com.monitor.call.domain.enums.BillingCycle;
import com.monitor.call.domain.enums.Role;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicensePlanEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.PaymentMethodEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicensePlanJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.PaymentMethodJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.superadmin.name:Super Admin}")
    private String superAdminName;

    @Value("${app.superadmin.email:}")
    private String superAdminEmail;

    @Value("${app.superadmin.password:}")
    private String superAdminPassword;

    private final UserJpaRepository userRepo;
    private final LicensePlanJpaRepository planRepo;
    private final PaymentMethodJpaRepository paymentMethodRepo;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    public DataInitializer(UserJpaRepository userRepo, LicensePlanJpaRepository planRepo,
                           PaymentMethodJpaRepository paymentMethodRepo,
                           PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        this.userRepo = userRepo;
        this.planRepo = planRepo;
        this.paymentMethodRepo = paymentMethodRepo;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateUserRolesConstraint();
        migrateLicensesStatusConstraint();
        seedSuperAdmin();
        seedInitialPlans();
        seedPaymentMethods();
    }

    /**
     * Actualiza el CHECK constraint de user_roles para incluir SUPER_ADMIN.
     * Se ejecuta siempre pero solo modifica la BD si el constraint aún no lo contempla.
     */
    private void migrateUserRolesConstraint() {
        try {
            jdbc.execute("""
                DO $$
                BEGIN
                  IF EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conname = 'user_roles_role_check'
                      AND pg_get_constraintdef(oid) NOT LIKE '%SUPER_ADMIN%'
                  ) THEN
                    ALTER TABLE user_roles DROP CONSTRAINT user_roles_role_check;
                    ALTER TABLE user_roles ADD CONSTRAINT user_roles_role_check
                      CHECK (role IN ('ADMIN','SALES_AGENT','CALL_AGENT','SUPER_ADMIN'));
                    RAISE NOTICE 'Constraint user_roles_role_check actualizado con SUPER_ADMIN';
                  END IF;
                END
                $$;
                """);
            logger.info("Constraint user_roles_role_check verificado/actualizado");
        } catch (Exception e) {
            logger.warn("No se pudo actualizar el constraint user_roles_role_check: {}", e.getMessage());
        }
    }

    private void migrateLicensesStatusConstraint() {
        try {
            jdbc.execute("""
                DO $$
                BEGIN
                  IF EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conname = 'licenses_status_check'
                      AND pg_get_constraintdef(oid) NOT LIKE '%PENDING%'
                  ) THEN
                    ALTER TABLE licenses DROP CONSTRAINT licenses_status_check;
                    ALTER TABLE licenses ADD CONSTRAINT licenses_status_check
                      CHECK (status IN ('PENDING','TRIAL','ACTIVE','EXPIRED','SUSPENDED'));
                    RAISE NOTICE 'Constraint licenses_status_check actualizado con PENDING';
                  END IF;
                END
                $$;
                """);
            logger.info("Constraint licenses_status_check verificado/actualizado");
        } catch (Exception e) {
            logger.warn("No se pudo actualizar el constraint licenses_status_check: {}", e.getMessage());
        }
    }

    private void seedSuperAdmin() {
        if (superAdminEmail == null || superAdminEmail.isBlank()) {
            logger.info("SUPER_ADMIN_EMAIL no configurado — omitiendo creación del super admin");
            return;
        }
        if (superAdminPassword == null || superAdminPassword.isBlank()) {
            logger.warn("SUPER_ADMIN_PASSWORD no configurado — omitiendo creación del super admin");
            return;
        }

        if (userRepo.findByEmail(superAdminEmail).isPresent()) {
            logger.info("Super admin ya existe: {}", superAdminEmail);
            return;
        }

        userRepo.save(UserEntity.builder()
                .name(superAdminName)
                .email(superAdminEmail)
                .password(passwordEncoder.encode(superAdminPassword))
                .active(true)
                .roles(Set.of(Role.SUPER_ADMIN))
                .mustChangePassword(false)
                .build());

        logger.info("Super admin creado exitosamente: {}", superAdminEmail);
    }

    private void seedInitialPlans() {
        if (!planRepo.existsByName("Mensual")) {
            planRepo.save(LicensePlanEntity.builder()
                    .name("Mensual")
                    .description("Plan mensual estándar")
                    .defaultMaxAgents(5)
                    .price(new BigDecimal("185000"))
                    .billingCycle(BillingCycle.MONTHLY)
                    .durationDays(30)
                    .active(true)
                    .build());
            logger.info("Plan mensual inicial creado: 185,000 COP");
        }
    }

    private void seedPaymentMethods() {
        if (!paymentMethodRepo.existsByName("Transferencia bancaria")) {
            paymentMethodRepo.save(PaymentMethodEntity.builder()
                    .name("Transferencia bancaria")
                    .details("Banco: [Nombre del banco]\nCuenta de ahorros: [Número de cuenta]\nTitular: [Nombre del titular]")
                    .active(true)
                    .build());
            logger.info("Método de pago inicial creado: Transferencia bancaria");
        }
    }
}
