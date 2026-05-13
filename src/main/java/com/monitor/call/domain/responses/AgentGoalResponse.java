package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import lombok.*;

import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGoalResponse {
    private Long id;
    private Long adminId;
    private Long agentId;
    private String agentName;   // null si aplica a todos
    private GoalKpi kpiType;
    private GoalPeriod period;
    private Double targetValue;
    private Boolean active;
    private OffsetDateTime createdAt;
}
