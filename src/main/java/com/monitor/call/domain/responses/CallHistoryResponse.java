package com.monitor.call.domain.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallHistoryResponse {
    private Long id;
    private String callId;
    private String callerExtension;
    private String callerIdNum;
    private String callerIdName;
    private String calledNumber;
    private String callStatus;
    private String callFlow;
    private OffsetDateTime createdAt;
    private Long agentId;
    private String agentName;
    private String agentExtension;
    private String typificationResult;
    private String typificationNotes;
    private String callbackDate;
    private Long leadId;
    private String leadContactName;
    private String leadContactPhone;
}
