package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.BillingCycle;
import com.monitor.call.domain.enums.LicenseStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder
public class MyLicenseResponse {
    private Long licenseId;
    private String planName;
    private BillingCycle billingCycle;
    private Integer durationDays;
    private BigDecimal price;
    private Integer maxCallAgents;
    private Integer maxSalesAgents;
    private BigDecimal priceMonthly;
    /** Tarifa unitaria por Call Agent (del plan) — para que el admin calcule el costo de expansión */
    private BigDecimal pricePerCallAgent;
    /** Tarifa unitaria por Sales Agent (del plan) */
    private BigDecimal pricePerSalesAgent;
    private LicenseStatus currentStatus;
    private OffsetDateTime expiresAt;
    private OffsetDateTime startDate;
}
