package com.monitor.call.domain.responses;

import lombok.*;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentResponse {
    private Long id;
    private Long userId;
    private String extension;
    private String name;
    private String email;
    private Boolean active;
    private Long groupId;
    private String groupName;
    private OffsetDateTime createdAt;
}
