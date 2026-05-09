package com.monitor.call.infrastructure.requests;

import com.monitor.call.domain.enums.CallResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CallTypificationRequest {
    @NotBlank(message = "El callId es requerido")
    private String callId;

    @NotNull(message = "El resultado es requerido")
    private CallResult result;

    private Long leadId;
    private String contactName;
    private String contactPhone;
    private String notes;

    /** Solo requerido cuando result = CALLBACK */
    private LocalDate callbackDate;
}
