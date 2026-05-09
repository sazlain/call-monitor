package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallTypificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallTypificationJpaRepository extends JpaRepository<CallTypificationEntity, Long> {

    Optional<CallTypificationEntity> findByCallId(String callId);
    boolean existsByCallId(String callId);
    List<CallTypificationEntity> findByAgentId(Long agentId);
    List<CallTypificationEntity> findByLeadId(Long leadId);

    @Query("SELECT t FROM CallTypificationEntity t WHERE t.agentId = :agentId AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    List<CallTypificationEntity> findByAgentAndPeriod(@Param("agentId") Long agentId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query("SELECT t.result, COUNT(t) FROM CallTypificationEntity t WHERE t.agentId = :agentId AND t.createdAt BETWEEN :from AND :to GROUP BY t.result")
    List<Object[]> countByResultForAgent(@Param("agentId") Long agentId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

    @Query(value = "SELECT e.call_id FROM call_events e WHERE e.call_status = 'HANGUP' AND e.caller_extension IN (SELECT a.extension FROM agents a WHERE a.group_id IN (SELECT ag.id FROM agent_groups ag WHERE ag.admin_id = :adminId)) AND e.call_id NOT IN (SELECT t.call_id FROM call_typifications t) AND e.created_at BETWEEN :from AND :to ORDER BY e.created_at DESC", nativeQuery = true)
    List<String> findUntypifiedCallIds(@Param("adminId") Long adminId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
