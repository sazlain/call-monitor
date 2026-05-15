package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.in.AgentUseCases;
import com.monitor.call.domain.exceptions.BusinessRuleException;
import com.monitor.call.domain.exceptions.ConflictException;
import com.monitor.call.domain.exceptions.NotFoundException;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.LicenseRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.AgentResponse;
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
    private final LicenseRepositoryPort licenseRepo;
    private final PasswordEncoder passwordEncoder;

    public AgentImpl(AgentRepositoryPort agentRepo,
                     UserRepositoryPort userRepo,
                     LicenseRepositoryPort licenseRepo,
                     PasswordEncoder passwordEncoder) {
        this.agentRepo = agentRepo;
        this.userRepo = userRepo;
        this.licenseRepo = licenseRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public AgentResponse createAgent(String name, String email, String extension,
                                     Long groupId, Long adminId) {
        if (userRepo.existsByEmail(email))
            throw new ConflictException("El email ya esta registrado");
        if (agentRepo.existsByExtension(extension))
            throw new ConflictException("La extensión " + extension + " ya está en uso por otro agente");

        if (adminId != null) {
            licenseRepo.findByAdminId(adminId).ifPresent(license -> {
                int current = agentRepo.findByAdminId(adminId).size();
                if (current >= license.getMaxAgents()) {
                    throw new BusinessRuleException("AGENT_LIMIT_REACHED: límite de "
                            + license.getMaxAgents() + " agentes alcanzado para este plan");
                }
            });
        }

        String tempPassword = UUID.randomUUID().toString().substring(0, 10);
        User user = User.builder()
                .name(name).email(email)
                .password(passwordEncoder.encode(tempPassword))
                .active(true)
                .roles(Set.of(Role.CALL_AGENT))
                .mustChangePassword(true)
                .build();
        User savedUser = userRepo.save(user);

        Agent agent = Agent.builder()
                .userId(savedUser.getId())
                .extension(extension)
                .groupId(groupId)
                .active(true).build();
        Agent savedAgent = agentRepo.save(agent);

        logger.info("Agente creado: extension={} email={} contrasena_temp={}", extension, email, tempPassword);

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
                .orElseThrow(() -> new NotFoundException("Agente no encontrado"));

        if (!agent.getExtension().equals(extension) && agentRepo.existsByExtension(extension))
            throw new ConflictException("La extensión " + extension + " ya está en uso por otro agente");

        agent.setExtension(extension);

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
                .orElseThrow(() -> new NotFoundException("Agente no encontrado"));
        agentRepo.deactivate(agentId);
        logger.info("Agente desactivado: {}", agentId);
    }

    @Override
    @Transactional
    public void reactivateAgent(Long agentId, Long adminId) {
        agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado"));
        agentRepo.reactivate(agentId);
        logger.info("Agente reactivado: {}", agentId);
    }

    @Override
    public List<AgentResponse> listAllAgentsByAdmin(Long adminId) {
        return toResponseList(agentRepo.findAllByAdminId(adminId));
    }

    private AgentResponse toResponse(Agent agent) {
        return userRepo.findById(agent.getUserId())
                .map(u -> AgentMapper.domainToResponseWithName(agent, u.getName(), u.getEmail()))
                .orElse(AgentMapper.domainToResponseWithName(agent, "Desconocido", ""));
    }

    private List<AgentResponse> toResponseList(List<Agent> agents) {
        List<Long> userIds = agents.stream().map(Agent::getUserId).toList();
        Map<Long, User> userMap = userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return agents.stream()
                .map(a -> {
                    User u = userMap.get(a.getUserId());
                    return AgentMapper.domainToResponseWithName(a,
                            u != null ? u.getName() : "Desconocido",
                            u != null ? u.getEmail() : "");
                })
                .toList();
    }
}
