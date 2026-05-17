package com.monitor.call.infrastructure.requests;

import lombok.Data;

@Data
public class CreateAdminWithLicenseRequest {
    private String name;
    private String email;
    private Long planId;
    private Integer maxAgents;
    private Integer maxCallAgents;
    private Integer maxSalesAgents;
    private String notes;
}
