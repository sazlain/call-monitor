package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.models.Agent;
import java.util.List;
import java.util.Optional;

public interface AgentRepositoryPort {
    Agent save(Agent agent);
    Optional<Agent> findById(Long id);
    Optional<Agent> findByExtension(String extension);
    Optional<Agent> findByUserId(Long userId);
    List<Agent> findByGroupId(Long groupId);
    List<Agent> findByAdminId(Long adminId);
    List<String> findExtensionsByGroupId(Long groupId);
    List<String> findExtensionsByAdminId(Long adminId);
    boolean existsByExtension(String extension);
    void deactivate(Long agentId);
}
