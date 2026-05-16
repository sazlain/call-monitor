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
    private LicenseStatus currentStatus;
    private OffsetDateTime expiresAt;
    private OffsetDateTime startDate;
}
