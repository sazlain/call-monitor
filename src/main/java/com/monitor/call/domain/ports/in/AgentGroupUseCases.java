package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.AgentGroupResponse;
import java.util.List;

public interface AgentGroupUseCases {
    AgentGroupResponse createGroup(String name, String description, Long adminId);
    AgentGroupResponse getGroup(Long groupId, Long adminId);
    List<AgentGroupResponse> listGroupsByAdmin(Long adminId);
    AgentGroupResponse updateGroup(Long groupId, String name, String description, Long adminId);
    void deactivateGroup(Long groupId, Long adminId);
    AgentGroupResponse assignAgentToGroup(Long groupId, Long agentId, Long adminId);
    AgentGroupResponse removeAgentFromGroup(Long groupId, Long agentId, Long adminId);
}
