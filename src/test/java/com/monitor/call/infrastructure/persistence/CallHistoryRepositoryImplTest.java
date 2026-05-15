package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.responses.CallHistoryPage;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.CallHistoryRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallHistoryRepositoryImplTest {

    @Mock private CallEventJpaRepository jpaRepo;
    @InjectMocks private CallHistoryRepositoryImpl repo;

    private OffsetDateTime from() { return OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC); }
    private OffsetDateTime to()   { return OffsetDateTime.of(2026, 5, 13, 23, 59, 59, 0, ZoneOffset.UTC); }

    // ─── findHistory ───────────────────────────────────────────────────────

    @Test
    void findHistory_noResults_returnsEmptyPage() {
        Page<Object[]> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 25), 0);
        when(jpaRepo.findHistory(isNull(), isNull(), any(), any(), any()))
                .thenReturn(emptyPage);

        CallHistoryPage result = repo.findHistory(null, null, from(), to(), 0, 25);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(25);
    }

    @Test
    void findHistory_withRows_mapsToCallHistoryResponse() {
        Object[] row = new Object[]{
            1L,           // [0]  id
            "CALL-001",   // [1]  callId
            "API-XYZ",    // [2]  callApiId
            "1001",       // [3]  callerExtension
            "5551001",    // [4]  callerIdNum
            "Contact",    // [5]  callerIdName
            "5550001",    // [6]  calledNumber
            "ANSWER",     // [7]  callStatus (outcome)
            "out",        // [8]  callFlow
            OffsetDateTime.of(2026, 5, 10, 10, 0, 0, 0, ZoneOffset.UTC), // [9] createdAt
            65,           // [10] durationSeconds
            10L,          // [11] agentId
            "Agent Ana",  // [12] agentName
            "1001",       // [13] agentExtension
            "SALE",       // [14] typificationResult
            "Good call",  // [15] typificationNotes
            null,         // [16] callbackDate
            5L,           // [17] leadId
            "Juan",       // [18] leadContactName
            "5551000"     // [19] leadContactPhone
        };
        Page<Object[]> page = new PageImpl<Object[]>(List.<Object[]>of(row), PageRequest.of(0, 25), 1);
        when(jpaRepo.findHistory(isNull(), isNull(), any(), any(), any()))
                .thenReturn(page);

        CallHistoryPage result = repo.findHistory(null, null, from(), to(), 0, 25);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCallId()).isEqualTo("CALL-001");
        assertThat(result.getContent().get(0).getCallApiId()).isEqualTo("API-XYZ");
        assertThat(result.getContent().get(0).getCallStatus()).isEqualTo("ANSWER");
        assertThat(result.getContent().get(0).getDurationSeconds()).isEqualTo(65);
        assertThat(result.getContent().get(0).getAgentName()).isEqualTo("Agent Ana");
        assertThat(result.getContent().get(0).getTypificationResult()).isEqualTo("SALE");
    }

    @Test
    void findHistory_withExtensionFilter_passesNonNullExtension() {
        Page<Object[]> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 25), 0);
        when(jpaRepo.findHistory(eq("1001"), isNull(), any(), any(), any()))
                .thenReturn(emptyPage);

        CallHistoryPage result = repo.findHistory("1001", null, from(), to(), 0, 25);

        assertThat(result).isNotNull();
        verify(jpaRepo).findHistory(eq("1001"), isNull(), any(), any(), any());
    }

    @Test
    void findHistory_blankExtension_treatedAsNull() {
        Page<Object[]> emptyPage = new PageImpl<>(List.of(), PageRequest.of(1, 10), 0);
        when(jpaRepo.findHistory(isNull(), isNull(), any(), any(), any()))
                .thenReturn(emptyPage);

        CallHistoryPage result = repo.findHistory("  ", null, from(), to(), 1, 10);

        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        verify(jpaRepo).findHistory(isNull(), isNull(), any(), any(), any());
    }

    @Test
    void findHistory_withStatusFilter_passesStatus() {
        Page<Object[]> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 25), 0);
        when(jpaRepo.findHistory(isNull(), eq("HANGUP"), any(), any(), any()))
                .thenReturn(emptyPage);

        CallHistoryPage result = repo.findHistory(null, "HANGUP", from(), to(), 0, 25);

        assertThat(result).isNotNull();
        verify(jpaRepo).findHistory(isNull(), eq("HANGUP"), any(), any(), any());
    }

    @Test
    void findHistory_paginationMetadataIsCorrect() {
        Page<Object[]> page = new PageImpl<>(List.of(), PageRequest.of(2, 10), 30);
        when(jpaRepo.findHistory(isNull(), isNull(), any(), any(), any()))
                .thenReturn(page);

        CallHistoryPage result = repo.findHistory(null, null, from(), to(), 2, 10);

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(30L);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }
}
