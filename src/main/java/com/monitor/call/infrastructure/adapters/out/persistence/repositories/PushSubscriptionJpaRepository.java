package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.PushSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionJpaRepository extends JpaRepository<PushSubscriptionEntity, Long> {

    Optional<PushSubscriptionEntity> findByUserIdAndEndpoint(Long userId, String endpoint);

    List<PushSubscriptionEntity> findByUserId(Long userId);

    @Transactional
    void deleteByUserIdAndEndpoint(Long userId, String endpoint);

    @Query("""
        SELECT ps FROM PushSubscriptionEntity ps
        WHERE ps.userId IN (
            SELECT a.userId FROM AgentEntity a WHERE a.id IN :agentIds
        )
    """)
    List<PushSubscriptionEntity> findByAgentIds(@Param("agentIds") List<Long> agentIds);
}
