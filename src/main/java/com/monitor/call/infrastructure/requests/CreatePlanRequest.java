package com.monitor.call.infrastructure.requests;

import com.monitor.call.domain.enums.BillingCycle;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePlanRequest {
    private String name;
    private String description;
    private Integer defaultMaxAgents;
    private BigDecimal price;
    private BillingCycle billingCycle;
    private Integer durationDays;
}
