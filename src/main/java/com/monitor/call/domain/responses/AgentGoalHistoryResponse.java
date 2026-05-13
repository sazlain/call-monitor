package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGoalHistoryResponse {
    private Long id;
    private Long goalId;
    private GoalKpi kpiType;
    private GoalPeriod period;
    private Long agentId;
    private String agentName;
    private LocalDate snapshotDate;
    private Double targetValue;
    private Double actualValue;
    private Double progressPercent;
    private Boolean achieved;
    private OffsetDateTime createdAt;
}
