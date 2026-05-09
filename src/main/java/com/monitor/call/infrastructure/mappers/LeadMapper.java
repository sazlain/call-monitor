package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.domain.responses.LeadResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallTypificationEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;

public class LeadMapper {

    public static Lead entityToDomain(LeadEntity e) {
        return Lead.builder()
                .id(e.getId()).contactName(e.getContactName())
                .contactPhone(e.getContactPhone()).leadSource(e.getLeadSource())
                .notes(e.getNotes()).leadDate(e.getLeadDate())
                .ownerId(e.getOwnerId()).assignedAgentId(e.getAssignedAgentId())
                .status(e.getStatus()).callbackDate(e.getCallbackDate())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    public static LeadEntity domainToEntity(Lead l) {
        return LeadEntity.builder()
                .id(l.getId()).contactName(l.getContactName())
                .contactPhone(l.getContactPhone()).leadSource(l.getLeadSource())
                .notes(l.getNotes()).leadDate(l.getLeadDate())
                .ownerId(l.getOwnerId()).assignedAgentId(l.getAssignedAgentId())
                .status(l.getStatus()).callbackDate(l.getCallbackDate())
                .build();
    }

    public static LeadResponse domainToResponse(Lead l, String ownerName, String assignedAgentName) {
        return LeadResponse.builder()
                .id(l.getId()).contactName(l.getContactName())
                .contactPhone(l.getContactPhone()).leadSource(l.getLeadSource())
                .notes(l.getNotes()).leadDate(l.getLeadDate())
                .ownerId(l.getOwnerId()).ownerName(ownerName)
                .assignedAgentId(l.getAssignedAgentId()).assignedAgentName(assignedAgentName)
                .status(l.getStatus()).callbackDate(l.getCallbackDate())
                .createdAt(l.getCreatedAt()).updatedAt(l.getUpdatedAt())
                .build();
    }

    public static CallTypification typEntityToDomain(CallTypificationEntity e) {
        return CallTypification.builder()
                .id(e.getId()).callId(e.getCallId()).agentId(e.getAgentId())
                .leadId(e.getLeadId()).result(e.getResult())
                .contactName(e.getContactName()).contactPhone(e.getContactPhone())
                .notes(e.getNotes()).callbackDate(e.getCallbackDate())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    public static CallTypificationEntity typDomainToEntity(CallTypification t) {
        return CallTypificationEntity.builder()
                .id(t.getId()).callId(t.getCallId()).agentId(t.getAgentId())
                .leadId(t.getLeadId()).result(t.getResult())
                .contactName(t.getContactName()).contactPhone(t.getContactPhone())
                .notes(t.getNotes()).callbackDate(t.getCallbackDate())
                .build();
    }

    public static CallTypificationResponse typDomainToResponse(CallTypification t, String agentName) {
        return CallTypificationResponse.builder()
                .id(t.getId()).callId(t.getCallId()).agentId(t.getAgentId())
                .agentName(agentName).leadId(t.getLeadId()).result(t.getResult())
                .contactName(t.getContactName()).contactPhone(t.getContactPhone())
                .notes(t.getNotes()).callbackDate(t.getCallbackDate())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
