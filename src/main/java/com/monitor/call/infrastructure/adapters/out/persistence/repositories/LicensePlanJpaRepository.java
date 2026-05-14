package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicensePlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LicensePlanJpaRepository extends JpaRepository<LicensePlanEntity, Long> {
    List<LicensePlanEntity> findByActiveTrue();
    boolean existsByName(String name);
}
