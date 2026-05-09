package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.LeadStatus;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LeadResponse {
    private Long id;
    private String contactName;
    private String contactPhone;
    private String leadSource;
    private String notes;
    private LocalDate leadDate;
    private Long ownerId;
    private String ownerName;
    private Long assignedAgentId;
    private String assignedAgentName;
    private LeadStatus status;
    private LocalDate callbackDate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
