package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.in.AgentUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.AgentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.mappers.AgentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentImpl implements AgentUseCases {

    private static final Logger logger = LoggerFactory.getLogger(AgentImpl.class);

    private final AgentRepositoryPort agentRepo;
    private final UserRepositoryPort userRepo;
    private final UserJpaRepository userJpaRepo;
    private final PasswordEncoder passwordEncoder;
    private final LicenseJpaRepository licenseRepo;

    public AgentImpl(AgentRepositoryPort agentRepo, UserRepositoryPort userRepo,
                     UserJpaRepository userJpaRepo, PasswordEncoder passwordEncoder,
                     LicenseJpaRepository licenseRepo) {
        this.agentRepo = agentRepo;
        this.userRepo = userRepo;
        this.userJpaRepo = userJpaRepo;
        this.passwordEncoder = passwordEncoder;
        this.licenseRepo = licenseRepo;
    }

    @Override
    @Transactional
    public AgentResponse createAgent(String name, String email, String extension,
                                     Long groupId, Long adminId) {
        if (userRepo.existsByEmail(email))
            throw new RuntimeException("El email ya esta registrado");
        if (agentRepo.existsByExtension(extension))
            throw new RuntimeException("La extensión " + extension + " ya está en uso por otro agente");

        // Verificar límite de agentes según licencia
        if (adminId != null) {
            LicenseEntity license = licenseRepo.findByAdminId(adminId).orElse(null);
            if (license != null) {
                int current = agentRepo.findByAdminId(adminId).size();
                if (current >= license.getMaxAgents()) {
                    throw new RuntimeException("AGENT_LIMIT_REACHED: límite de " + license.getMaxAgents() + " agentes alcanzado para este plan");
                }
            }
        }

        // 1. Crear usuario con contrasena temporal y roles CALL_AGENT
        String tempPassword = UUID.randomUUID().toString().substring(0, 10);
        User user = User.builder()
                .name(name).email(email)
                .password(passwordEncoder.encode(tempPassword))
                .active(true)
                .roles(Set.of(Role.CALL_AGENT))
                .mustChangePassword(true)
                .build();
        User savedUser = userRepo.save(user);

        // 2. Crear agente vinculado al usuario
        Agent agent = Agent.builder()
                .userId(savedUser.getId())
                .extension(extension)
                .groupId(groupId)
                .active(true).build();
        Agent savedAgent = agentRepo.save(agent);

        logger.info("Agente creado: extension={} email={} contrasena_temp={}", extension, email, tempPassword);
        // TODO: enviar email con contrasena temporal al agente

        return toResponse(savedAgent);
    }

    @Override
    public AgentResponse getAgent(Long agentId) {
        Agent agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));
        return toResponse(agent);
    }

    @Override
    public AgentResponse getAgentByExtension(String extension) {
        Agent agent = agentRepo.findByExtension(extension)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado: " + extension));
        return toResponse(agent);
    }

    @Override
    public List<AgentResponse> listAgentsByGroup(Long groupId) {
        return toResponseList(agentRepo.findByGroupId(groupId));
    }

    @Override
    public List<AgentResponse> listAgentsByAdmin(Long adminId) {
        return toResponseList(agentRepo.findByAdminId(adminId));
    }

    @Override
    @Transactional
    public AgentResponse updateAgent(Long agentId, String name, String extension, Long adminId) {
        Agent agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));

        if (!agent.getExtension().equals(extension) && agentRepo.existsByExtension(extension))
            throw new RuntimeException("La extensión " + extension + " ya está en uso por otro agente");

        agent.setExtension(extension);

        // Actualizar nombre en el usuario
        userRepo.findById(agent.getUserId()).ifPresent(u -> {
            u.setName(name);
            userRepo.save(u);
        });

        return toResponse(agentRepo.save(agent));
    }

    @Override
    @Transactional
    public void deactivateAgent(Long agentId, Long adminId) {
        agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));
        agentRepo.deactivate(agentId);
        logger.info("Agente desactivado: {}", agentId);
    }

    private AgentResponse toResponse(Agent agent) {
        var userMap = userJpaRepo.findAllById(List.of(agent.getUserId())).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u));
        return AgentMapper.domainToResponse(agent, userMap);
    }

    private List<AgentResponse> toResponseList(List<Agent> agents) {
        var userIds = agents.stream().map(Agent::getUserId).toList();
        var userMap = userJpaRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u));
        return agents.stream().map(a -> AgentMapper.domainToResponse(a, userMap)).toList();
    }
}
