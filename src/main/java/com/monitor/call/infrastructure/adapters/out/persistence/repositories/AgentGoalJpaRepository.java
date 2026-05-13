package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.domain.enums.GoalPeriod;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AgentGoalJpaRepository extends JpaRepository<AgentGoalEntity, Long> {

    List<AgentGoalEntity> findByAdminIdAndActiveTrue(Long adminId);

    List<AgentGoalEntity> findByAdminIdAndAgentIdAndActiveTrue(Long adminId, Long agentId);

    /** Metas activas aplicables a un agente: las globales (agentId=null) + las específicas suyas */
    @Query("SELECT g FROM AgentGoalEntity g WHERE g.active = true AND g.adminId = :adminId " +
           "AND (g.agentId IS NULL OR g.agentId = :agentId)")
    List<AgentGoalEntity> findActiveGoalsForAgent(@Param("adminId") Long adminId,
                                                  @Param("agentId") Long agentId);

    /** Todas las metas DAILY activas (para evaluación al cierre del día) */
    List<AgentGoalEntity> findByActiveTrueAndPeriod(GoalPeriod period);
}
