package com.monitor.call.infrastructure.persistence.impl;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.CallEventListenerRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallEventListenerRepositoryTest {

    @Mock
    private CallEventJpaRepository callEventJpaRepository;

    @InjectMocks
    private CallEventListenerRepository repository;

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CallEvent buildDomainEvent(String callId, CallStatus status) {
        return CallEvent.builder()
                .callId(callId)
                .callerExtension("1001")
                .callerIdNum("5551001")
                .calledNumber("5550001")
                .callStatus(status)
                .callFlow(CallFlow.out)
                .build();
    }

    private CallEventEntity buildSavedEntity(Long id, String callId, CallStatus status) {
        return CallEventEntity.builder()
                .id(id).callId(callId)
                .callerExtension("1001").callerIdNum("5551001")
                .callStatus(status).callFlow(CallFlow.out)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    // ─── saveCallEvent ────────────────────────────────────────────────────────────

    @Test
    void saveCallEvent_mapsToEntityAndCallsJpaRepo() {
        CallEvent domain = buildDomainEvent("CALL-001", CallStatus.CALLING);
        CallEventEntity saved = buildSavedEntity(1L, "CALL-001", CallStatus.CALLING);
        when(callEventJpaRepository.save(any())).thenReturn(saved);

        CallEvent result = repository.saveCallEvent(domain);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCallId()).isEqualTo("CALL-001");
        assertThat(result.getCallStatus()).isEqualTo(CallStatus.CALLING);
    }

    @Test
    void saveCallEvent_propagatesCallIdToEntity() {
        CallEvent domain = buildDomainEvent("CALL-XYZ", CallStatus.ANSWER);
        CallEventEntity saved = buildSavedEntity(2L, "CALL-XYZ", CallStatus.ANSWER);
        when(callEventJpaRepository.save(any())).thenReturn(saved);

        ArgumentCaptor<CallEventEntity> captor = ArgumentCaptor.forClass(CallEventEntity.class);

        repository.saveCallEvent(domain);

        verify(callEventJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCallId()).isEqualTo("CALL-XYZ");
    }

    @Test
    void saveCallEvent_propagatesCallStatusToEntity() {
        CallEvent domain = buildDomainEvent("CALL-001", CallStatus.HANGUP);
        CallEventEntity saved = buildSavedEntity(3L, "CALL-001", CallStatus.HANGUP);
        when(callEventJpaRepository.save(any())).thenReturn(saved);

        ArgumentCaptor<CallEventEntity> captor = ArgumentCaptor.forClass(CallEventEntity.class);
        repository.saveCallEvent(domain);

        verify(callEventJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCallStatus()).isEqualTo(CallStatus.HANGUP);
    }

    @Test
    void saveCallEvent_propagatesCallerExtensionToEntity() {
        CallEvent domain = buildDomainEvent("CALL-001", CallStatus.CALLING);
        CallEventEntity saved = buildSavedEntity(4L, "CALL-001", CallStatus.CALLING);
        when(callEventJpaRepository.save(any())).thenReturn(saved);

        ArgumentCaptor<CallEventEntity> captor = ArgumentCaptor.forClass(CallEventEntity.class);
        repository.saveCallEvent(domain);

        verify(callEventJpaRepository).save(captor.capture());
        assertThat(captor.getValue().getCallerExtension()).isEqualTo("1001");
    }

    @Test
    void saveCallEvent_returnsEntityMappedToDomain_withCreatedAt() {
        CallEvent domain = buildDomainEvent("CALL-001", CallStatus.CALLING);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CallEventEntity saved = buildSavedEntity(5L, "CALL-001", CallStatus.CALLING);
        when(callEventJpaRepository.save(any())).thenReturn(saved);

        CallEvent result = repository.saveCallEvent(domain);

        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    void saveCallEvent_callsJpaRepoExactlyOnce() {
        CallEvent domain = buildDomainEvent("CALL-001", CallStatus.CALLING);
        CallEventEntity saved = buildSavedEntity(1L, "CALL-001", CallStatus.CALLING);
        when(callEventJpaRepository.save(any())).thenReturn(saved);

        repository.saveCallEvent(domain);

        verify(callEventJpaRepository, times(1)).save(any());
        verifyNoMoreInteractions(callEventJpaRepository);
    }
}
