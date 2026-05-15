package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.AgentResponse;
import java.util.List;

public interface AgentUseCases {
    /** Crea el agente y su usuario asociado con contrasena temporal */
    AgentResponse createAgent(String name, String email, String extension, Long groupId, Long adminId);
    AgentResponse getAgent(Long agentId);
    AgentResponse getAgentByExtension(String extension);
    List<AgentResponse> listAgentsByGroup(Long groupId);
    List<AgentResponse> listAgentsByAdmin(Long adminId);
    AgentResponse updateAgent(Long agentId, String name, String extension, Long adminId);
    void deactivateAgent(Long agentId, Long adminId);
    void reactivateAgent(Long agentId, Long adminId);
    List<AgentResponse> listAllAgentsByAdmin(Long adminId);
}
