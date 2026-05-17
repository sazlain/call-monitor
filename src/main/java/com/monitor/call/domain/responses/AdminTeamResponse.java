package com.monitor.call.domain.responses;

import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminTeamResponse {

    private List<CallAgentEntry> callAgents;
    private List<SalesAgentEntry> salesAgents;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CallAgentEntry {
        private Long agentId;
        private Long userId;
        private String name;
        private String email;
        private String extension;
        private Boolean active;
        private String groupName;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SalesAgentEntry {
        private Long id;
        private String name;
        private String email;
        private Boolean active;
        private String defaultCallAgentName;
    }
}
