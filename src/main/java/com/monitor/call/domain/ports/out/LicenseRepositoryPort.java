package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.models.License;

import java.util.Optional;

public interface LicenseRepositoryPort {
    Optional<License> findByAdminId(Long adminId);
}
