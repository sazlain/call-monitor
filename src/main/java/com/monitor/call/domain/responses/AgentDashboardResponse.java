package com.monitor.call.domain.responses;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentDashboardResponse {

    private String extension;
    private String agentName;
    private Boolean isActive;

    // Bloque 1: Volumen
    private Long totalCalls;
    private Long answeredCalls;
    private Long missedCalls;
    private Long outboundCalls;
    private Long inboundCalls;
    private Double answerRate;

    // Bloque 2: Tiempo
    private Long totalDurationSeconds;
    private Double avgDurationSeconds;
    private Long maxDurationSeconds;
    private Long minDurationSeconds;
    private Long shortCalls;
    private Long longCalls;

    // Bloque 3: Ritmo
    private Double callsPerHour;

    // Bloque 4: Tipificacion
    private Long typifiedCalls;
    private Long untypifiedCalls;
    private List<ResultCount> resultDistribution;

    // Bloque 7: Tendencias
    private List<HourlyCount> callsByHour;
    private List<DailyCount> callsByDay;
    private List<DowCount> callsByDayOfWeek;

    // Recientes
    private List<RecentCallResponse> recentCalls;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HourlyCount {
        private Integer hour;
        private Long count;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DailyCount {
        private String date;
        private Long count;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DowCount {
        private Integer dayOfWeek;
        private String dayName;
        private Long count;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ResultCount {
        private String result;
        private Long count;
    }
}
