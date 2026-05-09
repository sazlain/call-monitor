package com.monitor.call.domain.responses;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminDashboardResponse {

    private String adminEmail;
    private Integer totalGroups;
    private Integer totalAgents;
    private Integer activeAgents;

    // KPIs globales
    private Long totalCalls;
    private Long answeredCalls;
    private Long missedCalls;
    private Double answerRate;
    private Long totalDurationSeconds;
    private Double avgDurationSeconds;

    // Por grupo
    private List<GroupSummary> groups;

    // Ranking de agentes
    private List<AgentSummary> agentRanking;

    // Tendencia diaria
    private List<DailyTrend> dailyTrend;

    // Alertas activas
    private List<String> alerts;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GroupSummary {
        private Long groupId;
        private String groupName;
        private Integer totalAgents;
        private Integer activeAgents;
        private Long totalCalls;
        private Long answeredCalls;
        private Double answerRate;
        private Long totalDurationSeconds;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AgentSummary {
        private String extension;
        private String agentName;
        private String groupName;
        private Boolean isActive;
        private Long totalCalls;
        private Long answeredCalls;
        private Double answerRate;
        private Long totalDurationSeconds;
        private Double avgDurationSeconds;
        private Double conversionRate;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DailyTrend {
        private String date;
        private String extension;
        private String agentName;
        private Long count;
    }
}
