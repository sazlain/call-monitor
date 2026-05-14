package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.LicenseStatus;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data @Builder
public class AdminSummaryResponse {
    private Long adminId;
    private String name;
    private String email;
    private Boolean active;
    private Integer agentCount;
    private LicenseResponse license;
    private OffsetDateTime createdAt;
}
