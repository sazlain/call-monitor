package com.monitor.call.infrastructure.requests;

import com.monitor.call.domain.enums.BillingCycle;
import com.monitor.call.domain.enums.LicenseStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class CreateAdminWithLicenseRequest {
    private String name;
    private String email;
    private String password;
    private String planName;
    private Integer maxAgents;
    private LicenseStatus status;
    private BillingCycle billingCycle;
    private BigDecimal priceMonthly;
    private OffsetDateTime expirationDate;
    private String notes;
}
