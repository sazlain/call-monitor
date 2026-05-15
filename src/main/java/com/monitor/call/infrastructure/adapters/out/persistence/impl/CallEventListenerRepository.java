package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.out.CallEventListenerRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CallEventListenerRepository implements CallEventListenerRepositoryPort {


    private static Logger logger = LoggerFactory.getLogger(CallEventListenerRepository.class);

    private final CallEventJpaRepository callEventJpaRepository;

    public CallEventListenerRepository(CallEventJpaRepository callEventJpaRepository) {
        this.callEventJpaRepository = callEventJpaRepository;
    }

    @Override
    public CallEvent saveCallEvent(CallEvent callEvent) {
        CallEventEntity callEventEntity = CallEventMapper.domainToEntity(callEvent);
        logger.info("Saving call event: {}", callEvent);
        try {
            CallEventEntity newCallEventEntity = callEventJpaRepository.save(callEventEntity);
            logger.info("Call event guardado OK: id={} callId={} status={} ext={}",
                    newCallEventEntity.getId(),
                    newCallEventEntity.getCallId(),
                    newCallEventEntity.getCallStatus(),
                    newCallEventEntity.getCallerExtension());
            return CallEventMapper.entityToDomain(newCallEventEntity);
        } catch (Exception e) {
            logger.error("ERROR al guardar call event en BD: callId={} status={} ext={} — {}",
                    callEvent.getCallId(), callEvent.getCallStatus(),
                    callEvent.getCallerExtension(), e.getMessage(), e);
            throw e;
        }
    }
}
