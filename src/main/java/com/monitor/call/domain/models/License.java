package com.monitor.call.domain.models;

import com.monitor.call.domain.enums.LicenseStatus;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class License {
    private Long id;
    private Long adminId;
    private LicenseStatus status;
    private Integer maxAgents;
    private Integer maxCallAgents;
    private Integer maxSalesAgents;
}
