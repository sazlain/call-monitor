package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.models.AgentGroup;
import java.util.List;
import java.util.Optional;

public interface AgentGroupRepositoryPort {
    AgentGroup save(AgentGroup group);
    Optional<AgentGroup> findById(Long id);
    Optional<AgentGroup> findByIdAndAdminId(Long id, Long adminId);
    List<AgentGroup> findByAdminId(Long adminId);
    boolean existsByNameAndAdminId(String name, Long adminId);
    void deactivate(Long groupId);
}
