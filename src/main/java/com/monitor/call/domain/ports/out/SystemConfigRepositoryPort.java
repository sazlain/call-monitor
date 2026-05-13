package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.models.SystemConfig;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepositoryPort {

    List<SystemConfig> findByAdminId(Long adminId);

    Optional<SystemConfig> findByAdminIdAndKey(Long adminId, String key);

    SystemConfig save(SystemConfig config);

    boolean existsByAdminIdAndKey(Long adminId, String key);
}
