package com.monitor.call.domain.usecases;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.in.CallEventListenerUseCases;
import com.monitor.call.domain.ports.out.CallEventListenerRepositoryPort;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.exceptions.SupportMessages;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CallEventListenerImpl  implements CallEventListenerUseCases {
    Logger logger = LoggerFactory.getLogger(CallEventListenerImpl.class);

    private final CallEventListenerRepositoryPort callEventListenerRepositoryPort;
    private final SupportMessages supportMessages;

    public CallEventListenerImpl(CallEventListenerRepositoryPort callEventListenerRepositoryPort, SupportMessages supportMessages) {
        this.callEventListenerRepositoryPort = callEventListenerRepositoryPort;
        this.supportMessages = supportMessages;
    }

    @Override
    public CallEventListenerResponse onCallStarted(CallEvent callEvent) {
        // Handle call started event
        logger.info("Received call started event: {}", callEvent);
        return CallEventMapper.domainToResponse(callEventListenerRepositoryPort.saveCallEvent(callEvent));
    }
}