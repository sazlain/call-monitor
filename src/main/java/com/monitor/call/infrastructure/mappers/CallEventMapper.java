package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.requests.CallEventRequest;

import java.util.HashMap;
import java.util.Map;

import static com.monitor.call.domain.enums.CallFlow.IN;
import static com.monitor.call.domain.enums.CallStatus.CALLING;

public class CallEventMapper {

    public static CallEventEntity domainToEntity(CallEvent callEvent) {
        return CallEventEntity.builder()
                .callId(callEvent.getCallId())
                .callerIdNum(callEvent.getCallerIdNum())
                .callerIdName(callEvent.getCallerIdName())
                .calledDID(callEvent.getCalledDID())
                .calledExtension(callEvent.getCalledExtension())
                .callStatus(callEvent.getCallStatus())
                .callFlow(callEvent.getCallFlow())
                .callerExtension(callEvent.getCallerExtension())
                .calledNumber(callEvent.getCalledNumber())
                .callAPIID(callEvent.getCallAPIID())
                .build();
    }

    public static CallEvent requestToDomain(CallEventRequest request) {
        return CallEvent.builder()
                .callId(request.getCallId())
                .callerIdNum(request.getCallerIdNum())
                .callerIdName(request.getCallerIdName())
                .calledDID(request.getCalledDID())
                .calledExtension(request.getCalledExtension())
                .callStatus(CallStatus.valueOf(request.getCallStatus()))
                .callFlow(CallFlow.valueOf(request.getCallFlow()))
                .callerExtension(request.getCallerExtension())
                .calledNumber(request.getCalledNumber())
                .callAPIID(request.getCallAPIID())
                .build();
    }

    public static CallEventListenerResponse domainToResponse(CallEvent callEvent) {
        return CallEventListenerResponse.builder()
                .callId(callEvent.getCallId())
                .status(callEvent.getCallStatus().name())
                .build();
    }

    public static CallEvent entityToDomain(CallEventEntity callEventEntity) {
        return CallEvent.builder()
                .callId(callEventEntity.getCallId())
                .callerIdNum(callEventEntity.getCallerIdNum())
                .callerIdName(callEventEntity.getCallerIdName())
                .calledDID(callEventEntity.getCalledDID())
                .calledExtension(callEventEntity.getCalledExtension())
                .callStatus(callEventEntity.getCallStatus())
                .callFlow(callEventEntity.getCallFlow())
                .callerExtension(callEventEntity.getCallerExtension())
                .calledNumber(callEventEntity.getCalledNumber())
                .callAPIID(callEventEntity.getCallAPIID())
                .build();
    }

    public static CallEventRequest payloadToRequest(Map<String, String> payload) {
        return CallEventRequest.builder()
                .callId(payload.get("callId"))
                .callerIdNum(payload.get("callerIdNum"))
                .callerIdName(payload.get("callerIdName"))
                .calledDID(payload.get("calledDID"))
                .calledExtension(payload.get("calledExtension"))
                .callStatus(payload.get("callStatus"))
                .callFlow(payload.get("callFlow"))
                .callerExtension(payload.get("callerExtension"))
                .calledNumber(payload.get("calledNumber"))
                .callAPIID(payload.get("callAPIID"))
                .build();
    }
}
