package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentStatusResponse {
    private List<AgentCurrentStatus> agents;
    private Integer totalActive;
    private Integer totalIdle;
    private OffsetDateTime asOf;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AgentCurrentStatus {
        private String extension;
        private String agentName;
        private String groupName;
        private Boolean isActive;
        private CallStatus currentCallStatus;
        private CallFlow currentCallFlow;
        private String currentCallId;
        private OffsetDateTime callStartedAt;
        private Long callDurationSeconds;
    }
}
