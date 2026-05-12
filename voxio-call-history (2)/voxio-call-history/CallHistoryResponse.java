package com.monitor.call.domain.responses;

import lombok.*;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallHistoryResponse {
    private Long        id;
    private String      callId;
    private String      callerExtension;
    private String      callerIdNum;
    private String      callerIdName;
    private String      calledNumber;
    private String      callStatus;
    private String      callFlow;
    private OffsetDateTime createdAt;

    // Datos del agente
    private Long        agentId;
    private String      agentName;
    private String      agentExtension;

    // Tipificación asociada (puede ser null)
    private String      typificationResult;
    private String      typificationNotes;
    private String      callbackDate;

    // Lead asociado (puede ser null)
    private Long        leadId;
    private String      leadContactName;
    private String      leadContactPhone;
}
