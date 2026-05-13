package com.monitor.call.domain.models;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import lombok.*;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGoal {
    private Long id;
    private Long adminId;
    /** null = aplica a todos los agentes del admin */
    private Long agentId;
    private GoalKpi kpiType;
    private GoalPeriod period;
    private Double targetValue;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
