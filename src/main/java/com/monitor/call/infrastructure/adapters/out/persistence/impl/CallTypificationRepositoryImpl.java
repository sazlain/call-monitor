package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.ports.out.CallTypificationRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallTypificationJpaRepository;
import com.monitor.call.infrastructure.mappers.LeadMapper;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Component
public class CallTypificationRepositoryImpl implements CallTypificationRepositoryPort {

    private final CallTypificationJpaRepository repo;
    public CallTypificationRepositoryImpl(CallTypificationJpaRepository repo) { this.repo = repo; }

    @Override public CallTypification save(CallTypification t) {
        return LeadMapper.typEntityToDomain(repo.save(LeadMapper.typDomainToEntity(t)));
    }
    @Override public Optional<CallTypification> findByCallId(String callId) {
        return repo.findByCallId(callId).map(LeadMapper::typEntityToDomain);
    }
    @Override public boolean existsByCallId(String callId) {
        return repo.existsByCallId(callId);
    }
    @Override public List<CallTypification> findByAgentId(Long agentId) {
        return repo.findByAgentId(agentId).stream().map(LeadMapper::typEntityToDomain).toList();
    }
    @Override public List<CallTypification> findByLeadId(Long leadId) {
        return repo.findByLeadId(leadId).stream().map(LeadMapper::typEntityToDomain).toList();
    }
    @Override public List<CallTypification> findByAgentAndPeriod(Long agentId, OffsetDateTime from, OffsetDateTime to) {
        return repo.findByAgentAndPeriod(agentId, from, to).stream().map(LeadMapper::typEntityToDomain).toList();
    }
    @Override public List<Object[]> countByResultForAgent(Long agentId, OffsetDateTime from, OffsetDateTime to) {
        return repo.countByResultForAgent(agentId, from, to);
    }
    @Override public List<String> findUntypifiedCallIds(Long adminId, OffsetDateTime from, OffsetDateTime to) {
        return repo.findUntypifiedCallIds(adminId, from, to);
    }
}
