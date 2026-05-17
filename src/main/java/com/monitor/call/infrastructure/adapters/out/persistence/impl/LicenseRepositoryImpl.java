package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.models.License;
import com.monitor.call.domain.ports.out.LicenseRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LicenseRepositoryImpl implements LicenseRepositoryPort {

    private final LicenseJpaRepository repo;

    public LicenseRepositoryImpl(LicenseJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Optional<License> findByAdminId(Long adminId) {
        return repo.findByAdminId(adminId).map(e -> License.builder()
                .id(e.getId())
                .adminId(e.getAdminId())
                .status(e.getStatus())
                .maxAgents(e.getMaxAgents())
                .maxCallAgents(e.getMaxCallAgents())
                .maxSalesAgents(e.getMaxSalesAgents())
                .build());
    }
}
