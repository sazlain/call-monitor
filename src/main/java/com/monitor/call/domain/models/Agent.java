package com.monitor.call.domain.models;

import lombok.*;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Agent {
    private Long id;
    private Long userId;
    private Long adminId;
    private String extension;
    private String userName;
    private String userEmail;
    private Boolean active;
    private Long groupId;
    private String groupName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
