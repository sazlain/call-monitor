package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.responses.CallEventListenerResponse;

public interface CallHistoryUseCases {
    /** Mantiene onCallStarted por compatibilidad pero delega a onCallEvent */
    CallEventListenerResponse findAllHistory(CallEvent callEvent);

    /** Nuevo metodo unificado — maneja todos los CallStatus */
    CallEventListenerResponse onCallEvent(CallEvent callEvent);
}
