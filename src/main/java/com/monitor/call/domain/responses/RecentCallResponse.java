package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import lombok.*;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RecentCallResponse {
    private String callId;
    private String callerIdNum;
    private String callerIdName;
    private String calledNumber;
    private CallStatus finalStatus;
    private CallFlow callFlow;
    private Long durationSeconds;
    private OffsetDateTime startedAt;
}
