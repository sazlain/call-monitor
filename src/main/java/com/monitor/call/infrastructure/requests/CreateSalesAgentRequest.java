package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSalesAgentRequest {

    @NotBlank
    private String name;

    @NotBlank @Email
    private String email;

    /** ID del Agent (CALL_AGENT) que se asignará por defecto a los leads de este sales agent */
    private Long defaultCallAgentId;
}
