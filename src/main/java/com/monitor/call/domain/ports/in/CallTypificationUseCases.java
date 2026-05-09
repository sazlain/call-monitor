package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.infrastructure.requests.CallTypificationRequest;
import java.util.List;

public interface CallTypificationUseCases {
    CallTypificationResponse typify(CallTypificationRequest request, Long agentId);
    CallTypificationResponse updateTypification(String callId, CallTypificationRequest request, Long agentId);
    CallTypificationResponse getByCallId(String callId);
    List<CallTypificationResponse> listByAgent(Long agentId);
    List<CallTypificationResponse> listByLead(Long leadId);
}
