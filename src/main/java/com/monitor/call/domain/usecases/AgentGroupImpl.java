package com.monitor.call.domain.usecases;

import com.monitor.call.domain.models.AgentGroup;
import com.monitor.call.domain.ports.in.AgentGroupUseCases;
import com.monitor.call.domain.ports.out.AgentGroupRepositoryPort;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.responses.AgentGroupResponse;
import com.monitor.call.domain.responses.AgentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.mappers.AgentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgentGroupImpl implements AgentGroupUseCases {

    private static final Logger logger = LoggerFactory.getLogger(AgentGroupImpl.class);

    private final AgentGroupRepositoryPort groupRepo;
    private final AgentRepositoryPort agentRepo;
    private final UserJpaRepository userJpaRepository;

    public AgentGroupImpl(AgentGroupRepositoryPort groupRepo,
                          AgentRepositoryPort agentRepo,
                          UserJpaRepository userJpaRepository) {
        this.groupRepo = groupRepo;
        this.agentRepo = agentRepo;
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    @Transactional
    public AgentGroupResponse createGroup(String name, String description, Long adminId) {
        if (groupRepo.existsByNameAndAdminId(name, adminId))
            throw new RuntimeException("Ya existe un grupo con ese nombre");

        AgentGroup group = AgentGroup.builder()
                .name(name).description(description)
                .adminId(adminId).active(true).build();

        AgentGroup saved = groupRepo.save(group);
        logger.info("Grupo creado: {} por admin {}", name, adminId);
        return toResponse(saved);
    }

    @Override
    public AgentGroupResponse getGroup(Long groupId, Long adminId) {
        AgentGroup group = groupRepo.findByIdAndAdminId(groupId, adminId)
                .orElseThrow(() -> new RuntimeException("Grupo no encontrado"));
        return toResponse(group);
    }

    @Override
    public List<AgentGroupResponse> listGroupsByAdmin(Long adminId) {
        return groupRepo.findByAdminId(adminId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public AgentGroupResponse updateGroup(Long groupId, String name, String description, Long adminId) {
        AgentGroup group = groupRepo.findByIdAndAdminId(groupId, adminId)
                .orElseThrow(() -> new RuntimeException("Grupo no encontrado"));
        group.setName(name);
        group.setDescription(description);
        return toResponse(groupRepo.save(group));
    }

    @Override
    @Transactional
    public void deactivateGroup(Long groupId, Long adminId) {
        groupRepo.findByIdAndAdminId(groupId, adminId)
                .orElseThrow(() -> new RuntimeException("Grupo no encontrado"));
        groupRepo.deactivate(groupId);
        logger.info("Grupo desactivado: {} por admin {}", groupId, adminId);
    }

    @Override
    @Transactional
    public AgentGroupResponse assignAgentToGroup(Long groupId, Long agentId, Long adminId) {
        groupRepo.findByIdAndAdminId(groupId, adminId)
                .orElseThrow(() -> new RuntimeException("Grupo no encontrado"));

        var agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));

        agent.setGroupId(groupId);
        agentRepo.save(agent);
        logger.info("Agente {} asignado al grupo {}", agentId, groupId);

        return getGroup(groupId, adminId);
    }

    @Override
    @Transactional
    public AgentGroupResponse removeAgentFromGroup(Long groupId, Long agentId, Long adminId) {
        groupRepo.findByIdAndAdminId(groupId, adminId)
                .orElseThrow(() -> new RuntimeException("Grupo no encontrado"));

        var agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));

        agent.setGroupId(null);
        agentRepo.save(agent);
        return getGroup(groupId, adminId);
    }

    private AgentGroupResponse toResponse(AgentGroup group) {
        List<com.monitor.call.domain.models.Agent> agents =
                group.getId() != null ? agentRepo.findByGroupId(group.getId()) : List.of();

        var userIds = agents.stream().map(a -> a.getUserId()).toList();
        var userMap = userJpaRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u));

        List<AgentResponse> agentResponses = agents.stream()
                .map(a -> AgentMapper.domainToResponse(a, userMap)).toList();

        return AgentGroupResponse.builder()
                .id(group.getId()).name(group.getName())
                .description(group.getDescription())
                .adminId(group.getAdminId()).active(group.getActive())
                .agentCount(agentResponses.size())
                .agents(agentResponses)
                .createdAt(group.getCreatedAt()).build();
    }
}
