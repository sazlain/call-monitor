package com.monitor.call.domain.models;

import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGoalHistory {
    private Long id;
    private Long goalId;
    private Long agentId;
    private LocalDate snapshotDate;
    private Double targetValue;
    private Double actualValue;
    private Boolean achieved;
    private OffsetDateTime createdAt;
}
