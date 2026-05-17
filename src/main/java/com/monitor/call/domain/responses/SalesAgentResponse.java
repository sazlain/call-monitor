package com.monitor.call.domain.responses;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class SalesAgentResponse {
    private Long   id;
    private String name;
    private String email;
    private Boolean active;
    private Long   adminId;
    private Long   defaultCallAgentId;
    private String defaultCallAgentName;
    private int    leadCount;
}
