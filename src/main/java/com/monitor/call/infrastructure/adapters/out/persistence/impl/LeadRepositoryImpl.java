package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.out.LeadRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.mappers.LeadMapper;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class LeadRepositoryImpl implements LeadRepositoryPort {

    private final LeadJpaRepository repo;
    public LeadRepositoryImpl(LeadJpaRepository repo) { this.repo = repo; }

    @Override public Lead save(Lead lead) {
        return LeadMapper.entityToDomain(repo.save(LeadMapper.domainToEntity(lead)));
    }
    @Override public List<Lead> saveAll(List<Lead> leads) {
        return repo.saveAll(leads.stream().map(LeadMapper::domainToEntity).toList())
                .stream().map(LeadMapper::entityToDomain).toList();
    }
    @Override public Optional<Lead> findById(Long id) {
        return repo.findById(id).map(LeadMapper::entityToDomain);
    }
    @Override public List<Lead> findByOwnerId(Long ownerId) {
        return repo.findByOwnerId(ownerId).stream().map(LeadMapper::entityToDomain).toList();
    }
    @Override public List<Lead> findByOwnerIdAndStatus(Long ownerId, LeadStatus status) {
        return repo.findByOwnerIdAndStatus(ownerId, status).stream().map(LeadMapper::entityToDomain).toList();
    }
    @Override public List<Lead> findByOwnerAndDateRange(Long ownerId, LocalDate from, LocalDate to) {
        return repo.findByOwnerAndDateRange(ownerId, from, to).stream().map(LeadMapper::entityToDomain).toList();
    }
    @Override public List<Lead> findAssignedPendingLeads(Long agentId) {
        return repo.findAssignedPendingLeads(agentId).stream().map(LeadMapper::entityToDomain).toList();
    }
    @Override public List<Lead> findAllActiveByAdminId(Long adminId) {
        return repo.findAllActiveByAdminId(adminId).stream().map(LeadMapper::entityToDomain).toList();
    }
    @Override public List<Lead> findPublicLeadsForAgent(Long adminId, Long agentId) {
        return repo.findPublicLeadsForAgent(adminId, agentId).stream().map(LeadMapper::entityToDomain).toList();
    }

    @Override
    public List<Lead> findPendingCallbacks(Long userId, Long agentId) {
        return repo.findPendingCallbacks(userId, agentId)
                .stream().map(LeadMapper::entityToDomain).toList();
    }

    @Override public List<Object[]> countByStatusForOwner(Long ownerId) {
        return repo.countByStatusForOwner(ownerId);
    }

    @Override
    public Optional<Lead> findActiveByPhone(String phone) {
        // 1. Exact match
        List<LeadEntity> exact = repo.findActiveByContactPhone(phone);
        if (!exact.isEmpty()) return exact.stream().findFirst().map(LeadMapper::entityToDomain);

        // 2. Suffix match — strip non-digits and use last 10 digits
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() >= 7) {
            String suffix = digits.substring(Math.max(0, digits.length() - 10));
            return repo.findActiveByContactPhoneSuffix(suffix)
                    .stream().findFirst().map(LeadMapper::entityToDomain);
        }
        return Optional.empty();
    }

    @Override
    public void updateStatus(Long leadId, LeadStatus status) {
        repo.findById(leadId).ifPresent(lead -> {
            lead.setStatus(status);
            repo.save(lead);
        });
    }
}
