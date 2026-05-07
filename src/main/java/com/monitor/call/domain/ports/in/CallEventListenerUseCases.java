package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.responses.CallEventListenerResponse;

public interface CallEventListenerUseCases {
    public CallEventListenerResponse onCallStarted(CallEvent callEvent);
}