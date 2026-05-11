package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface LeadJpaRepository extends JpaRepository<LeadEntity, Long>,
        JpaSpecificationExecutor<LeadEntity> {

    List<LeadEntity> findByOwnerId(Long ownerId);
    List<LeadEntity> findByAssignedAgentId(Long assignedAgentId);
    List<LeadEntity> findByOwnerIdAndStatus(Long ownerId, LeadStatus status);
    List<LeadEntity> findByAssignedAgentIdAndStatus(Long agentId, LeadStatus status);

    @Query("SELECT l FROM LeadEntity l WHERE l.status = 'CALLBACK' " +
            "AND (l.ownerId = :userId OR l.assignedAgentId = :agentId) " +
            "ORDER BY l.callbackDate ASC NULLS LAST")
    List<LeadEntity> findPendingCallbacks(
            @Param("userId") Long userId,
            @Param("agentId") Long agentId
    );

    @Query("SELECT l FROM LeadEntity l WHERE l.ownerId = :ownerId AND l.leadDate BETWEEN :from AND :to ORDER BY l.createdAt DESC")
    List<LeadEntity> findByOwnerAndDateRange(@Param("ownerId") Long ownerId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT l.status, COUNT(l) FROM LeadEntity l WHERE l.ownerId = :ownerId GROUP BY l.status")
    List<Object[]> countByStatusForOwner(@Param("ownerId") Long ownerId);

    @Query("SELECT l FROM LeadEntity l WHERE l.assignedAgentId = :agentId AND l.status IN ('PENDING', 'CALLBACK') ORDER BY l.callbackDate ASC NULLS LAST, l.createdAt ASC")
    List<LeadEntity> findAssignedPendingLeads(@Param("agentId") Long agentId);

    @Query("SELECT COUNT(l) > 0 FROM LeadEntity l WHERE l.contactPhone = :phone AND l.ownerId = :ownerId")
    boolean existsByPhoneAndOwner(@Param("phone") String phone, @Param("ownerId") Long ownerId);

    @Query("SELECT l FROM LeadEntity l WHERE l.contactPhone = :phone AND l.status NOT IN ('DISCARDED', 'CONVERTED') ORDER BY l.createdAt DESC")
    List<LeadEntity> findActiveByContactPhone(@Param("phone") String phone);
}
