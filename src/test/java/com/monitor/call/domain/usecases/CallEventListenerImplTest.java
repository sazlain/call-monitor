package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.out.CallEventListenerRepositoryPort;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallEventListenerImplTest {

    @Mock
    private CallEventListenerRepositoryPort callEventListenerRepositoryPort;

    @InjectMocks
    private CallEventListenerImpl callEventListenerImpl;

    private CallEvent buildEvent(String callId, CallStatus status) {
        return CallEvent.builder()
                .callId(callId)
                .callerExtension("1001")
                .callerIdNum("5551001")
                .callStatus(status)
                .callFlow(CallFlow.out)
                .build();
    }

    private CallEvent buildSavedEvent(Long id, String callId, CallStatus status) {
        return CallEvent.builder()
                .id(id)
                .callId(callId)
                .callerExtension("1001")
                .callStatus(status)
                .callFlow(CallFlow.out)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    @Test
    void onCallEvent_CALLING_savesAndReturnsResponse() {
        CallEvent event = buildEvent("CALL-001", CallStatus.CALLING);
        CallEvent saved = buildSavedEvent(1L, "CALL-001", CallStatus.CALLING);
        when(callEventListenerRepositoryPort.saveCallEvent(event)).thenReturn(saved);

        CallEventListenerResponse resp = callEventListenerImpl.onCallEvent(event);

        assertThat(resp.getCallId()).isEqualTo("CALL-001");
        assertThat(resp.getStatus()).isEqualTo("CALLING");
        verify(callEventListenerRepositoryPort).saveCallEvent(event);
    }

    @Test
    void onCallEvent_ANSWER_savesAndReturnsCorrectStatus() {
        CallEvent event = buildEvent("CALL-002", CallStatus.ANSWER);
        CallEvent saved = buildSavedEvent(2L, "CALL-002", CallStatus.ANSWER);
        when(callEventListenerRepositoryPort.saveCallEvent(event)).thenReturn(saved);

        CallEventListenerResponse resp = callEventListenerImpl.onCallEvent(event);

        assertThat(resp.getStatus()).isEqualTo("ANSWER");
        assertThat(resp.getCallId()).isEqualTo("CALL-002");
    }

    @Test
    void onCallEvent_HANGUP_savesAndReturnsCorrectStatus() {
        CallEvent event = buildEvent("CALL-003", CallStatus.HANGUP);
        CallEvent saved = buildSavedEvent(3L, "CALL-003", CallStatus.HANGUP);
        when(callEventListenerRepositoryPort.saveCallEvent(event)).thenReturn(saved);

        CallEventListenerResponse resp = callEventListenerImpl.onCallEvent(event);

        assertThat(resp.getStatus()).isEqualTo("HANGUP");
    }

    @Test
    void onCallStarted_delegatesToOnCallEvent_andSavesEvent() {
        CallEvent event = buildEvent("CALL-004", CallStatus.CALLING);
        CallEvent saved = buildSavedEvent(4L, "CALL-004", CallStatus.CALLING);
        when(callEventListenerRepositoryPort.saveCallEvent(event)).thenReturn(saved);

        CallEventListenerResponse resp = callEventListenerImpl.onCallStarted(event);

        assertThat(resp.getCallId()).isEqualTo("CALL-004");
        assertThat(resp.getStatus()).isEqualTo("CALLING");
        // onCallStarted delegates to onCallEvent — same repo call
        verify(callEventListenerRepositoryPort, times(1)).saveCallEvent(event);
    }

    @Test
    void onCallEvent_BUSY_savesAndReturnsCorrectStatus() {
        CallEvent event = buildEvent("CALL-005", CallStatus.BUSY);
        CallEvent saved = buildSavedEvent(5L, "CALL-005", CallStatus.BUSY);
        when(callEventListenerRepositoryPort.saveCallEvent(event)).thenReturn(saved);

        CallEventListenerResponse resp = callEventListenerImpl.onCallEvent(event);

        assertThat(resp.getStatus()).isEqualTo("BUSY");
    }

    @Test
    void onCallEvent_NOANSWER_savesCorrectly() {
        CallEvent event = buildEvent("CALL-006", CallStatus.NOANSWER);
        CallEvent saved = buildSavedEvent(6L, "CALL-006", CallStatus.NOANSWER);
        when(callEventListenerRepositoryPort.saveCallEvent(event)).thenReturn(saved);

        CallEventListenerResponse resp = callEventListenerImpl.onCallEvent(event);

        assertThat(resp.getStatus()).isEqualTo("NOANSWER");
    }

    @Test
    void onCallEvent_callsRepoExactlyOnce() {
        CallEvent event = buildEvent("CALL-007", CallStatus.CALLING);
        CallEvent saved = buildSavedEvent(7L, "CALL-007", CallStatus.CALLING);
        when(callEventListenerRepositoryPort.saveCallEvent(event)).thenReturn(saved);

        callEventListenerImpl.onCallEvent(event);

        verify(callEventListenerRepositoryPort, times(1)).saveCallEvent(event);
        verifyNoMoreInteractions(callEventListenerRepositoryPort);
    }
}
