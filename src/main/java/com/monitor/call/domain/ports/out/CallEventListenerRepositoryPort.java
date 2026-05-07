package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.models.CallEvent;

public interface CallEventListenerRepositoryPort {
    public CallEvent saveCallEvent(CallEvent callEvent);
}
