package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.AgentGroup;
import com.monitor.call.domain.responses.AgentGroupResponse;
import com.monitor.call.domain.responses.AgentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGroupEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AgentMapper {

    public static Agent entityToDomain(AgentEntity e) {
        return Agent.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .extension(e.getExtension())
                .active(e.getActive())
                .adminId(e.getGroup() != null ? e.getGroup().getAdminId() : null)
                .groupId(e.getGroup() != null ? e.getGroup().getId() : null)
                .groupName(e.getGroup() != null ? e.getGroup().getName() : null)
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public static AgentEntity domainToEntity(Agent a) {
        AgentEntity entity = new AgentEntity();
        entity.setId(a.getId());
        entity.setUserId(a.getUserId());
        entity.setExtension(a.getExtension());
        entity.setActive(a.getActive() != null ? a.getActive() : true);
        return entity;
    }

    /**
     * Convierte a response enriqueciendo con datos del usuario (nombre, email).
     * userMap: Map<userId, UserEntity> para evitar N+1 queries.
     */
    public static AgentResponse domainToResponse(Agent a, Map<Long, UserEntity> userMap) {
        UserEntity user = userMap.get(a.getUserId());
        return AgentResponse.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .extension(a.getExtension())
                .name(user != null ? user.getName() : "Desconocido")
                .email(user != null ? user.getEmail() : "")
                .active(a.getActive())
                .groupId(a.getGroupId())
                .groupName(a.getGroupName())
                .createdAt(a.getCreatedAt())
                .build();
    }

    public static AgentGroupResponse groupEntityToResponse(AgentGroupEntity g,
                                                            List<AgentResponse> agents) {
        return AgentGroupResponse.builder()
                .id(g.getId())
                .name(g.getName())
                .description(g.getDescription())
                .adminId(g.getAdminId())
                .active(g.getActive())
                .agentCount(agents != null ? agents.size() : 0)
                .agents(agents != null ? agents : Collections.emptyList())
                .createdAt(g.getCreatedAt())
                .build();
    }
}
