package com.monitor.call.domain.models;

import com.google.gson.Gson;
import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallEvent {

    private Long id;
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
    private OffsetDateTime createdAt;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
