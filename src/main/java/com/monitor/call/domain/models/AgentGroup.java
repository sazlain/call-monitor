package com.monitor.call.domain.models;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGroup {
    private Long id;
    private String name;
    private String description;
    private Long adminId;
    private Boolean active;
    private List<Agent> agents;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
