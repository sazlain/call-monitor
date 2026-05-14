package com.monitor.call.domain.responses;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class SuperAdminStatsResponse {
    private long totalAdmins;
    private long activeAdmins;
    private long totalAgents;
    private long pendingLicenses;
    private long activeLicenses;
    private long expiredLicenses;
    private long suspendedLicenses;
    private BigDecimal mrr;
}
