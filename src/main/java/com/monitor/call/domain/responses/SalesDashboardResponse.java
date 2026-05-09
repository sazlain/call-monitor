package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.LeadStatus;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SalesDashboardResponse {

    private String ownerName;
    private Long ownerId;

    // Bloque 11: Funnel de leads
    private Long totalLeads;
    private Long newLeads;
    private Long pendingLeads;
    private Long contactedLeads;
    private Long interestedLeads;
    private Long convertedLeads;
    private Long discardedLeads;
    private Long callbackLeads;
    private Double conversionRate;
    private Double contactRate;

    // Por origen (lead_source libre)
    private List<SourceSummary> leadsBySource;

    // Callbacks pendientes
    private Long pendingCallbacks;
    private Long overdueCallbacks;

    // Rendimiento de agentes asignados
    private List<AssignedAgentSummary> assignedAgents;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SourceSummary {
        private String source;
        private Long total;
        private Long converted;
        private Double conversionRate;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AssignedAgentSummary {
        private Long agentId;
        private String agentName;
        private Long assignedLeads;
        private Long contactedLeads;
        private Long convertedLeads;
        private Double conversionRate;
    }
}
