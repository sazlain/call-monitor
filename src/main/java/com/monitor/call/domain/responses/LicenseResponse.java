package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.BillingCycle;
import com.monitor.call.domain.enums.LicenseStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder
public class LicenseResponse {
    private Long id;
    private Long adminId;
    private String planName;
    private Integer maxAgents;
    private LicenseStatus status;
    private BillingCycle billingCycle;
    private BigDecimal priceMonthly;
    private OffsetDateTime startDate;
    private OffsetDateTime expirationDate;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
