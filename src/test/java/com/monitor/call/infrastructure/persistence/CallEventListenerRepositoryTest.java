package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.CallEventListenerRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallEventListenerRepositoryTest {

    @Mock private CallEventJpaRepository jpaRepo;
    @InjectMocks private CallEventListenerRepository repo;

    @Test
    void saveCallEvent_savesAndReturnsMappedDomain() {
        CallEvent domain = CallEvent.builder()
                .callId("CALL-001").callerIdNum("5551001").callerIdName("Contact")
                .calledNumber("5550001").callerExtension("1001")
                .callStatus(CallStatus.CALLING).callFlow(CallFlow.out)
                .build();

        CallEventEntity savedEntity = CallEventEntity.builder()
                .id(1L).callId("CALL-001").callerIdNum("5551001")
                .calledNumber("5550001").callerExtension("1001")
                .callStatus(CallStatus.CALLING).callFlow(CallFlow.out)
                .build();

        when(jpaRepo.save(any())).thenReturn(savedEntity);

        CallEvent result = repo.saveCallEvent(domain);

        assertThat(result.getCallId()).isEqualTo("CALL-001");
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCallStatus()).isEqualTo(CallStatus.CALLING);
        verify(jpaRepo).save(any());
    }

    @Test
    void saveCallEvent_propagatesCallId() {
        CallEvent domain = CallEvent.builder()
                .callId("CALL-002").callStatus(CallStatus.HANGUP).callFlow(CallFlow.in)
                .build();
        CallEventEntity saved = CallEventEntity.builder()
                .id(2L).callId("CALL-002").callStatus(CallStatus.HANGUP).callFlow(CallFlow.in)
                .build();
        when(jpaRepo.save(any())).thenReturn(saved);

        CallEvent result = repo.saveCallEvent(domain);

        assertThat(result.getCallId()).isEqualTo("CALL-002");
        assertThat(result.getCallStatus()).isEqualTo(CallStatus.HANGUP);
    }
}
