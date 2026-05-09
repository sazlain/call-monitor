package com.monitor.call.domain.usecases;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.in.CallEventListenerUseCases;
import com.monitor.call.domain.ports.out.CallEventListenerRepositoryPort;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CallEventListenerImpl implements CallEventListenerUseCases {

    private static final Logger logger = LoggerFactory.getLogger(CallEventListenerImpl.class);

    private final CallEventListenerRepositoryPort callEventListenerRepositoryPort;

    public CallEventListenerImpl(CallEventListenerRepositoryPort callEventListenerRepositoryPort) {
        this.callEventListenerRepositoryPort = callEventListenerRepositoryPort;
    }

    @Override
    public CallEventListenerResponse onCallStarted(CallEvent callEvent) {
        return onCallEvent(callEvent);
    }

    @Override
    public CallEventListenerResponse onCallEvent(CallEvent callEvent) {
        logger.info("Evento de llamada recibido: callId={} status={} extension={}",
                callEvent.getCallId(), callEvent.getCallStatus(), callEvent.getCallerExtension());
        CallEvent saved = callEventListenerRepositoryPort.saveCallEvent(callEvent);
        return CallEventMapper.domainToResponse(saved);
    }
}
