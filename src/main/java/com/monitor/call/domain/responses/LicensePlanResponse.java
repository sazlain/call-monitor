package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.BillingCycle;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder
public class LicensePlanResponse {
    private Long id;
    private String name;
    private String description;
    private Integer defaultMaxAgents;
    private BigDecimal price;
    private BillingCycle billingCycle;
    private Integer durationDays;
    private Boolean active;
    private OffsetDateTime createdAt;
}
