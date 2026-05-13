package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.models.SystemConfig;
import com.monitor.call.domain.ports.out.SystemConfigRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.SystemConfigEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.SystemConfigJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SystemConfigRepositoryImpl implements SystemConfigRepositoryPort {

    private final SystemConfigJpaRepository repo;

    public SystemConfigRepositoryImpl(SystemConfigJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<SystemConfig> findByAdminId(Long adminId) {
        return repo.findByAdminId(adminId).stream().map(this::toModel).toList();
    }

    @Override
    public Optional<SystemConfig> findByAdminIdAndKey(Long adminId, String key) {
        return repo.findByAdminIdAndConfigKey(adminId, key).map(this::toModel);
    }

    @Override
    public SystemConfig save(SystemConfig config) {
        return toModel(repo.save(toEntity(config)));
    }

    @Override
    public boolean existsByAdminIdAndKey(Long adminId, String key) {
        return repo.existsByAdminIdAndConfigKey(adminId, key);
    }

    private SystemConfig toModel(SystemConfigEntity e) {
        return SystemConfig.builder()
                .id(e.getId())
                .adminId(e.getAdminId())
                .configKey(e.getConfigKey())
                .configValue(e.getConfigValue())
                .defaultValue(e.getDefaultValue())
                .required(e.getRequired())
                .description(e.getDescription())
                .valueType(e.getValueType())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private SystemConfigEntity toEntity(SystemConfig m) {
        return SystemConfigEntity.builder()
                .id(m.getId())
                .adminId(m.getAdminId())
                .configKey(m.getConfigKey())
                .configValue(m.getConfigValue())
                .defaultValue(m.getDefaultValue())
                .required(m.getRequired() != null ? m.getRequired() : false)
                .description(m.getDescription())
                .valueType(m.getValueType() != null ? m.getValueType() : "STRING")
                .build();
    }
}
