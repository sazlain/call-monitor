package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGroupEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentGroupJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.mappers.AgentMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AgentRepositoryImpl implements AgentRepositoryPort {

    private final AgentJpaRepository agentRepo;
    private final AgentGroupJpaRepository groupRepo;

    public AgentRepositoryImpl(AgentJpaRepository agentRepo, AgentGroupJpaRepository groupRepo) {
        this.agentRepo = agentRepo;
        this.groupRepo = groupRepo;
    }

    @Override
    public Agent save(Agent agent) {
        AgentEntity entity = AgentMapper.domainToEntity(agent);
        if (agent.getGroupId() != null) {
            AgentGroupEntity group = groupRepo.findById(agent.getGroupId())
                    .orElseThrow(() -> new RuntimeException("Grupo no encontrado: " + agent.getGroupId()));
            entity.setGroup(group);
        }
        return AgentMapper.entityToDomain(agentRepo.save(entity));
    }

    @Override
    public Optional<Agent> findById(Long id) {
        return agentRepo.findById(id).map(AgentMapper::entityToDomain);
    }

    @Override
    public Optional<Agent> findByExtension(String extension) {
        return agentRepo.findByExtension(extension).map(AgentMapper::entityToDomain);
    }

    @Override
    public Optional<Agent> findByUserId(Long userId) {
        return agentRepo.findByUserId(userId).map(AgentMapper::entityToDomain);
    }

    @Override
    public List<Agent> findByGroupId(Long groupId) {
        return agentRepo.findByGroupIdAndActiveTrue(groupId).stream()
                .map(AgentMapper::entityToDomain).toList();
    }

    @Override
    public List<Agent> findByAdminId(Long adminId) {
        return agentRepo.findByAdminId(adminId).stream()
                .map(AgentMapper::entityToDomain).toList();
    }

    @Override
    public List<String> findExtensionsByGroupId(Long groupId) {
        return agentRepo.findExtensionsByGroupId(groupId);
    }

    @Override
    public List<String> findExtensionsByAdminId(Long adminId) {
        return agentRepo.findExtensionsByAdminId(adminId);
    }

    @Override
    public boolean existsByExtension(String extension) {
        return agentRepo.existsByExtension(extension);
    }

    @Override
    public void deactivate(Long agentId) {
        agentRepo.findById(agentId).ifPresent(a -> { a.setActive(false); agentRepo.save(a); });
    }
}
