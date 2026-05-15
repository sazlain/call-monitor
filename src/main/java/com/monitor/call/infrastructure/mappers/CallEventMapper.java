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
                .id(callEventEntity.getId())
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
                .createdAt(callEventEntity.getCreatedAt())
                .build();
    }

    /**
     * Convierte el payload del webhook al request interno.
     * El proveedor envía claves en PascalCase (CallStatus, CallerExtension, etc.)
     * pero para mayor robustez se hace lookup case-insensitive.
     */
    public static CallEventRequest payloadToRequest(Map<String, String> payload) {
        // Normalizar todas las claves a minúsculas para comparación case-insensitive
        Map<String, String> normalized = new java.util.HashMap<>();
        payload.forEach((k, v) -> normalized.put(k.toLowerCase(), v));

        String rawStatus = get(normalized, "callstatus");
        String rawFlow   = get(normalized, "callflow");

        if (rawStatus == null) throw new IllegalArgumentException("CallStatus ausente en el payload");
        if (rawFlow   == null) throw new IllegalArgumentException("CallFlow ausente en el payload");

        return CallEventRequest.builder()
                .callId(get(normalized, "callid"))
                .callerIdNum(get(normalized, "calleridnum"))
                .callerIdName(get(normalized, "calleridname"))
                .calledDID(get(normalized, "calleddid"))
                .calledExtension(get(normalized, "calledextension"))
                .callStatus(CallStatus.valueOf(rawStatus.toUpperCase()))
                .callFlow(CallFlow.valueOf(rawFlow.toLowerCase()))
                .callerExtension(get(normalized, "callerextension"))
                .calledNumber(get(normalized, "callednumber"))
                .callAPIID(get(normalized, "callapiid"))
                .build();
    }

    private static String get(Map<String, String> map, String key) {
        String v = map.get(key);
        return (v == null || v.isBlank()) ? null : v;
    }
}
