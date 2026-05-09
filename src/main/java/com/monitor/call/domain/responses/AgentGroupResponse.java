package com.monitor.call.domain.responses;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGroupResponse {
    private Long id;
    private String name;
    private String description;
    private Long adminId;
    private Boolean active;
    private Integer agentCount;
    private List<AgentResponse> agents;
    private OffsetDateTime createdAt;
}
