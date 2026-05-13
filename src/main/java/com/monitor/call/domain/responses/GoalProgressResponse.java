package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class GoalProgressResponse {
    private Long goalId;
    private GoalKpi kpiType;
    private GoalPeriod period;
    private Double targetValue;
    private Double actualValue;
    private Double progressPercent;
    private Boolean achieved;
}
