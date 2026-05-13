package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.requests.CallEventRequest;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallEventMapperTest {

    // ─── domainToEntity ───────────────────────────────────────────────────────

    @Test
    void domainToEntity_mapsAllFields() {
        CallEvent domain = CallEvent.builder()
                .callId("CALL-001").callerIdNum("5551001").callerIdName("Contact")
                .calledNumber("5550001").callerExtension("1001")
                .callStatus(CallStatus.CALLING).callFlow(CallFlow.out)
                .build();

        CallEventEntity entity = CallEventMapper.domainToEntity(domain);

        assertThat(entity.getCallId()).isEqualTo("CALL-001");
        assertThat(entity.getCallerIdNum()).isEqualTo("5551001");
        assertThat(entity.getCallerIdName()).isEqualTo("Contact");
        assertThat(entity.getCalledNumber()).isEqualTo("5550001");
        assertThat(entity.getCallerExtension()).isEqualTo("1001");
        assertThat(entity.getCallStatus()).isEqualTo(CallStatus.CALLING);
        assertThat(entity.getCallFlow()).isEqualTo(CallFlow.out);
    }

    @Test
    void domainToEntity_withOptionalFields_mapsCorrectly() {
        CallEvent domain = CallEvent.builder()
                .callId("CALL-002").callStatus(CallStatus.HANGUP).callFlow(CallFlow.in)
                .calledDID("DID-1").calledExtension("2001").callAPIID("API-1")
                .build();

        CallEventEntity entity = CallEventMapper.domainToEntity(domain);

        assertThat(entity.getCalledDID()).isEqualTo("DID-1");
        assertThat(entity.getCalledExtension()).isEqualTo("2001");
        assertThat(entity.getCallAPIID()).isEqualTo("API-1");
    }

    // ─── entityToDomain ───────────────────────────────────────────────────────

    @Test
    void entityToDomain_mapsAllFields() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CallEventEntity entity = CallEventEntity.builder()
                .id(1L).callId("CALL-001").callerIdNum("5551001").callerIdName("Name")
                .calledNumber("5550001").callerExtension("1001")
                .callStatus(CallStatus.ANSWER).callFlow(CallFlow.out)
                .createdAt(now)
                .build();

        CallEvent domain = CallEventMapper.entityToDomain(entity);

        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getCallId()).isEqualTo("CALL-001");
        assertThat(domain.getCallerIdNum()).isEqualTo("5551001");
        assertThat(domain.getCalledNumber()).isEqualTo("5550001");
        assertThat(domain.getCallerExtension()).isEqualTo("1001");
        assertThat(domain.getCallStatus()).isEqualTo(CallStatus.ANSWER);
        assertThat(domain.getCreatedAt()).isEqualTo(now);
    }

    // ─── requestToDomain ─────────────────────────────────────────────────────

    @Test
    void requestToDomain_mapsAllFields() {
        CallEventRequest req = CallEventRequest.builder()
                .callId("CALL-003").callerIdNum("5551003")
                .callStatus(CallStatus.HANGUP).callFlow(CallFlow.in)
                .callerExtension("1003").calledNumber("5550003")
                .build();

        CallEvent domain = CallEventMapper.requestToDomain(req);

        assertThat(domain.getCallId()).isEqualTo("CALL-003");
        assertThat(domain.getCallerIdNum()).isEqualTo("5551003");
        assertThat(domain.getCallStatus()).isEqualTo(CallStatus.HANGUP);
        assertThat(domain.getCallFlow()).isEqualTo(CallFlow.in);
        assertThat(domain.getCallerExtension()).isEqualTo("1003");
    }

    // ─── domainToResponse ────────────────────────────────────────────────────

    @Test
    void domainToResponse_mapsCallIdAndStatus() {
        CallEvent domain = CallEvent.builder()
                .callId("CALL-004").callStatus(CallStatus.NOANSWER).build();

        CallEventListenerResponse resp = CallEventMapper.domainToResponse(domain);

        assertThat(resp.getCallId()).isEqualTo("CALL-004");
        assertThat(resp.getStatus()).isEqualTo("NOANSWER");
    }

    @Test
    void domainToResponse_busyStatus_mapsCorrectly() {
        CallEvent domain = CallEvent.builder()
                .callId("CALL-005").callStatus(CallStatus.BUSY).build();

        CallEventListenerResponse resp = CallEventMapper.domainToResponse(domain);

        assertThat(resp.getStatus()).isEqualTo("BUSY");
    }

    // ─── payloadToRequest ────────────────────────────────────────────────────

    @Test
    void payloadToRequest_mapsPayloadMap() {
        Map<String, String> payload = Map.of(
                "CallID", "CALL-006",
                "CallerIDNum", "5551006",
                "CallerIDName", "Contact",
                "CalledNumber", "5550006",
                "CallerExtension", "1006",
                "CallStatus", "CALLING",
                "CallFlow", "out",
                "CalledDID", "",
                "CalledExtension", "",
                "CallAPIID", "API-6"
        );

        CallEventRequest req = CallEventMapper.payloadToRequest(payload);

        assertThat(req.getCallId()).isEqualTo("CALL-006");
        assertThat(req.getCallerIdNum()).isEqualTo("5551006");
        assertThat(req.getCallStatus()).isEqualTo(CallStatus.CALLING);
        assertThat(req.getCallFlow()).isEqualTo(CallFlow.out);
        assertThat(req.getCallAPIID()).isEqualTo("API-6");
    }
}
