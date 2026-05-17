package com.monitor.call.infrastructure.requests;

import com.monitor.call.domain.enums.BillingCycle;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdatePlanRequest {
    private String name;
    private String description;
    private Integer defaultMaxAgents;
    private Integer defaultMaxCallAgents;
    private Integer defaultMaxSalesAgents;
    private BigDecimal price;
    private BigDecimal pricePerCallAgent;
    private BigDecimal pricePerSalesAgent;
    private BillingCycle billingCycle;
    private Integer durationDays;
}
