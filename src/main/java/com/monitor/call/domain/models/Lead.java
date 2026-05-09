package com.monitor.call.domain.models;

import com.monitor.call.domain.enums.LeadStatus;
import lombok.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Lead {
    private Long id;
    private String contactName;
    private String contactPhone;
    private String leadSource;
    private String notes;
    private LocalDate leadDate;
    private Long ownerId;
    private Long assignedAgentId;
    private LeadStatus status;
    private LocalDate callbackDate;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
