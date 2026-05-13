package com.monitor.call.domain.usecases;

import com.monitor.call.domain.ports.out.CallHistoryRepositoryPort;
import com.monitor.call.domain.responses.CallHistoryPage;
import com.monitor.call.domain.responses.CallHistoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallHistoryImplTest {

    @Mock
    private CallHistoryRepositoryPort historyRepo;

    @InjectMocks
    private CallHistoryImpl callHistoryImpl;

    private CallHistoryPage emptyPage(int page, int size) {
        return CallHistoryPage.builder()
                .content(List.of()).totalElements(0L)
                .totalPages(0).page(page).size(size)
                .build();
    }

    @Test
    void getCallHistory_noFilters_delegatesToRepo() {
        CallHistoryPage expected = emptyPage(0, 25);
        when(historyRepo.findHistory(isNull(), isNull(), any(), any(), eq(0), eq(25)))
                .thenReturn(expected);

        CallHistoryPage result = callHistoryImpl.getCallHistory(null, null, null, null, 0, 25);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(25);
        verify(historyRepo).findHistory(isNull(), isNull(), any(), any(), eq(0), eq(25));
    }

    @Test
    void getCallHistory_withExtension_passesExtensionToRepo() {
        CallHistoryPage expected = emptyPage(0, 10);
        when(historyRepo.findHistory(eq("1001"), isNull(), any(), any(), eq(0), eq(10)))
                .thenReturn(expected);

        callHistoryImpl.getCallHistory("1001", null, null, null, 0, 10);

        verify(historyRepo).findHistory(eq("1001"), isNull(), any(), any(), eq(0), eq(10));
    }

    @Test
    void getCallHistory_withAllFilters_delegatesAllParams() {
        OffsetDateTime from = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to   = OffsetDateTime.of(2026, 1, 31, 23, 59, 0, 0, ZoneOffset.UTC);

        CallHistoryPage expected = CallHistoryPage.builder()
                .content(List.of(CallHistoryResponse.builder().callId("C-1").build()))
                .totalElements(1L).totalPages(1).page(0).size(25)
                .build();
        when(historyRepo.findHistory("1001", "HANGUP", from, to, 0, 25))
                .thenReturn(expected);

        CallHistoryPage result = callHistoryImpl.getCallHistory("1001", "HANGUP", from, to, 0, 25);

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCallId()).isEqualTo("C-1");
    }

    @Test
    void getCallHistory_secondPage_passesPageParamCorrectly() {
        CallHistoryPage expected = emptyPage(2, 10);
        when(historyRepo.findHistory(any(), any(), any(), any(), eq(2), eq(10)))
                .thenReturn(expected);

        CallHistoryPage result = callHistoryImpl.getCallHistory(null, null, null, null, 2, 10);

        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
    }

    @Test
    void getCallHistory_withStatusFilter_passesStatusToRepo() {
        CallHistoryPage expected = emptyPage(0, 25);
        when(historyRepo.findHistory(isNull(), eq("ANSWER"), any(), any(), eq(0), eq(25)))
                .thenReturn(expected);

        callHistoryImpl.getCallHistory(null, "ANSWER", null, null, 0, 25);

        verify(historyRepo).findHistory(isNull(), eq("ANSWER"), any(), any(), eq(0), eq(25));
    }
}
