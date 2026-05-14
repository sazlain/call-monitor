package com.monitor.call.infrastructure.services;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class LicenseExpirationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LicenseExpirationScheduler.class);

    private final LicenseJpaRepository licenseRepo;

    public LicenseExpirationScheduler(LicenseJpaRepository licenseRepo) {
        this.licenseRepo = licenseRepo;
    }

    // Todos los días a la 1:00 AM
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void expireOverdueLicenses() {
        OffsetDateTime now = OffsetDateTime.now();
        List<LicenseEntity> candidates = licenseRepo.findByStatusInAndExpirationDateBefore(
                List.of(LicenseStatus.ACTIVE, LicenseStatus.TRIAL), now);

        for (LicenseEntity lic : candidates) {
            lic.setStatus(LicenseStatus.EXPIRED);
            licenseRepo.save(lic);
            logger.warn("Licencia expirada: id={} adminId={} plan={}", lic.getId(), lic.getAdminId(), lic.getPlanName());
        }

        if (!candidates.isEmpty()) {
            logger.info("Expiración de licencias ejecutada: {} licencias marcadas como EXPIRED", candidates.size());
        }
    }
}
