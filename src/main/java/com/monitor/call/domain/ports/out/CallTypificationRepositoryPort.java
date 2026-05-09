package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.models.CallTypification;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CallTypificationRepositoryPort {
    CallTypification save(CallTypification typification);
    Optional<CallTypification> findByCallId(String callId);
    boolean existsByCallId(String callId);
    List<CallTypification> findByAgentId(Long agentId);
    List<CallTypification> findByLeadId(Long leadId);
    List<CallTypification> findByAgentAndPeriod(Long agentId, OffsetDateTime from, OffsetDateTime to);
    List<Object[]> countByResultForAgent(Long agentId, OffsetDateTime from, OffsetDateTime to);
    List<String> findUntypifiedCallIds(Long adminId, OffsetDateTime from, OffsetDateTime to);
}
