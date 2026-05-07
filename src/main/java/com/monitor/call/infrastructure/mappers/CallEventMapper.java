package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.requests.CallEventRequest;

import java.util.Map;

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
                .callStatus(request.getCallStatus())
                .callFlow(request.getCallFlow())
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
                .callId(payload.get("CallID"))
                .callerIdNum(payload.get("CallerIDNum"))
                .callerIdName(payload.get("CallerIDName"))
                .calledDID(payload.get("CalledDID"))
                .calledExtension(payload.get("CalledExtension"))
                .callStatus(CallStatus.valueOf(payload.get("CallStatus")))
                .callFlow(CallFlow.valueOf(payload.get("CallFlow")))
                .callerExtension(payload.get("CallerExtension"))
                .calledNumber(payload.get("CalledNumber"))
                .callAPIID(payload.get("CallAPIID"))
                .build();
    }
}
