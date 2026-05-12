package com.monitor.call.infrastructure.requests;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallEventRequest {

    private String callId;
    private String callerIdNum;
    private String callerIdName;
    private String calledDID;
    private String calledExtension;
    private CallStatus callStatus;
    private CallFlow callFlow;
    private String callerExtension;
    private String calledNumber;
    private String callAPIID;

}
