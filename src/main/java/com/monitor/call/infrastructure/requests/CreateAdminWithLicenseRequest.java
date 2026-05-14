package com.monitor.call.infrastructure.requests;

import lombok.Data;

@Data
public class CreateAdminWithLicenseRequest {
    private String name;
    private String email;
    private String password;
    private Long planId;
    private Integer maxAgents;
    private String notes;
}
