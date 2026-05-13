package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGoalHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgentGoalHistoryJpaRepository extends JpaRepository<AgentGoalHistoryEntity, Long> {

    List<AgentGoalHistoryEntity> findByAgentIdOrderBySnapshotDateDesc(Long agentId);

    List<AgentGoalHistoryEntity> findByAgentIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(
            Long agentId, LocalDate from, LocalDate to);

    List<AgentGoalHistoryEntity> findByGoalIdOrderBySnapshotDateDesc(Long goalId);

    Optional<AgentGoalHistoryEntity> findByGoalIdAndAgentIdAndSnapshotDate(
            Long goalId, Long agentId, LocalDate date);

    /** Historial completo del admin (todos sus agentes, todas sus metas) */
    List<AgentGoalHistoryEntity> findByGoalIdInOrderBySnapshotDateDesc(List<Long> goalIds);
}
