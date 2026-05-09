package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.CallResult;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CallTypificationResponse {
    private Long id;
    private String callId;
    private Long agentId;
    private String agentName;
    private Long leadId;
    private CallResult result;
    private String contactName;
    private String contactPhone;
    private String notes;
    private LocalDate callbackDate;
    private OffsetDateTime createdAt;
}
