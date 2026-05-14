package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.LicenseStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data @Builder
public class AdminSummaryResponse {
    private Long id;
    private String name;
    private String email;
    private Boolean active;
    private Integer usedAgents;
    private Integer maxAgents;
    private String planName;
    private LicenseStatus licenseStatus;
    private Long licenseId;
    private OffsetDateTime activatedAt;
    private OffsetDateTime expirationDate;
    private OffsetDateTime createdAt;
}
