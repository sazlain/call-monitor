package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.responses.BulkLeadResponse;
import com.monitor.call.domain.responses.LeadResponse;
import com.monitor.call.infrastructure.requests.CreateLeadRequest;
import java.time.LocalDate;
import java.util.List;

public interface LeadUseCases {
    LeadResponse createLead(CreateLeadRequest request, Long ownerId);
    BulkLeadResponse createBulkLeads(List<CreateLeadRequest> leads, Long ownerId, Long assignedAgentId);
    LeadResponse getLead(Long leadId);
    List<LeadResponse> listLeadsByOwner(Long ownerId, LeadStatus status, LocalDate from, LocalDate to);
    List<LeadResponse> listAssignedLeads(Long userId);  // recibe userId
    List<LeadResponse> listPendingCallbacks(Long userId);
    LeadResponse updateLead(Long leadId, CreateLeadRequest request, Long requesterId);
    LeadResponse assignLead(Long leadId, Long assignedAgentId, Long requesterId);
    LeadResponse takeLead(Long leadId, Long userId);
    void discardLead(Long leadId, Long requesterId);
    LeadResponse updateLeadStatus(Long leadId, LeadStatus status, LocalDate callbackDate);
    List<LeadResponse> findAllByPhone(String phone);
}
