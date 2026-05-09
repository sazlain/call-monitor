package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.models.AgentGroup;
import com.monitor.call.domain.ports.out.AgentGroupRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGroupEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentGroupJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AgentGroupRepositoryImpl implements AgentGroupRepositoryPort {

    private final AgentGroupJpaRepository repo;

    public AgentGroupRepositoryImpl(AgentGroupJpaRepository repo) { this.repo = repo; }

    @Override
    public AgentGroup save(AgentGroup group) {
        AgentGroupEntity entity = new AgentGroupEntity();
        entity.setId(group.getId());
        entity.setName(group.getName());
        entity.setDescription(group.getDescription());
        entity.setAdminId(group.getAdminId());
        entity.setActive(group.getActive() != null ? group.getActive() : true);
        AgentGroupEntity saved = repo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<AgentGroup> findById(Long id) {
        return repo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<AgentGroup> findByIdAndAdminId(Long id, Long adminId) {
        return repo.findByIdAndAdminId(id, adminId).map(this::toDomain);
    }

    @Override
    public List<AgentGroup> findByAdminId(Long adminId) {
        return repo.findByAdminIdAndActiveTrue(adminId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsByNameAndAdminId(String name, Long adminId) {
        return repo.existsByNameAndAdminId(name, adminId);
    }

    @Override
    public void deactivate(Long groupId) {
        repo.findById(groupId).ifPresent(g -> { g.setActive(false); repo.save(g); });
    }

    private AgentGroup toDomain(AgentGroupEntity e) {
        return AgentGroup.builder()
                .id(e.getId()).name(e.getName()).description(e.getDescription())
                .adminId(e.getAdminId()).active(e.getActive())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}
