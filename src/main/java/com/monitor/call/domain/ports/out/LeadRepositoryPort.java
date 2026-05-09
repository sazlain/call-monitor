package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Lead;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeadRepositoryPort {
    Lead save(Lead lead);
    List<Lead> saveAll(List<Lead> leads);
    Optional<Lead> findById(Long id);
    List<Lead> findByOwnerId(Long ownerId);
    List<Lead> findByOwnerIdAndStatus(Long ownerId, LeadStatus status);
    List<Lead> findByOwnerAndDateRange(Long ownerId, LocalDate from, LocalDate to);
    List<Lead> findAssignedPendingLeads(Long agentId);
    List<Lead> findPendingCallbacks(LocalDate today, Long userId, Long agentId);
    List<Object[]> countByStatusForOwner(Long ownerId);
}
