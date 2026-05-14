package com.monitor.call.infrastructure.config;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserJpaRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
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
}
