package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.SystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemConfigJpaRepository extends JpaRepository<SystemConfigEntity, Long> {

    List<SystemConfigEntity> findByAdminId(Long adminId);

    Optional<SystemConfigEntity> findByAdminIdAndConfigKey(Long adminId, String configKey);

    boolean existsByAdminIdAndConfigKey(Long adminId, String configKey);
}
