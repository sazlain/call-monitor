package com.monitor.call.infrastructure.requests;

import com.google.gson.Gson;
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
    private String callStatus;
    private String callFlow;
    private String callerExtension;
    private String calledNumber;
    private String callAPIID;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
