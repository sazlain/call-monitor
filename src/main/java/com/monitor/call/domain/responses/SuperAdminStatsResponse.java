package com.monitor.call.domain.responses;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class SuperAdminStatsResponse {
    private long totalAdmins;
    private long activeAdmins;
    private long totalAgents;
    private long activeLicenses;
    private long expiredLicenses;
    private long trialLicenses;
}
