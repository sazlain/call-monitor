package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface LicenseJpaRepository extends JpaRepository<LicenseEntity, Long> {

    Optional<LicenseEntity> findByAdminId(Long adminId);

    List<LicenseEntity> findByStatus(LicenseStatus status);

    List<LicenseEntity> findByStatusInAndExpirationDateBefore(
            List<LicenseStatus> statuses, OffsetDateTime date);
}
