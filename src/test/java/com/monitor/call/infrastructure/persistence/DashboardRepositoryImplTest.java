package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.DashboardRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardRepositoryImplTest {

    @Mock private CallEventJpaRepository repo;
    @InjectMocks private DashboardRepositoryImpl dashRepo;

    private OffsetDateTime from() { return OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC); }
    private OffsetDateTime to()   { return OffsetDateTime.of(2026, 5, 13, 23, 59, 59, 0, ZoneOffset.UTC); }

    // ─── Simple delegation methods ────────────────────────────────────────

    @Test
    void countTotalCalls_delegatesToRepo() {
        when(repo.countTotalCalls("1001", from(), to())).thenReturn(10L);
        assertThat(dashRepo.countTotalCalls("1001", from(), to())).isEqualTo(10L);
    }

    @Test
    void countAnsweredCalls_delegatesToRepo() {
        when(repo.countAnsweredCalls("1001", from(), to())).thenReturn(8L);
        assertThat(dashRepo.countAnsweredCalls("1001", from(), to())).isEqualTo(8L);
    }

    @Test
    void countMissedCalls_delegatesToRepo() {
        when(repo.countMissedCalls("1001", from(), to())).thenReturn(2L);
        assertThat(dashRepo.countMissedCalls("1001", from(), to())).isEqualTo(2L);
    }

    @Test
    void countOutboundCalls_delegatesToRepo() {
        when(repo.countOutboundCalls("1001", from(), to())).thenReturn(5L);
        assertThat(dashRepo.countOutboundCalls("1001", from(), to())).isEqualTo(5L);
    }

    @Test
    void countInboundCalls_delegatesToRepo() {
        when(repo.countInboundCalls("1001", from(), to())).thenReturn(5L);
        assertThat(dashRepo.countInboundCalls("1001", from(), to())).isEqualTo(5L);
    }

    @Test
    void sumDurationSeconds_delegatesToRepo() {
        when(repo.sumDurationSeconds("1001", from(), to())).thenReturn(3600.0);
        assertThat(dashRepo.sumDurationSeconds("1001", from(), to())).isEqualTo(3600.0);
    }

    @Test
    void maxDurationSeconds_delegatesToRepo() {
        when(repo.maxDurationSeconds("1001", from(), to())).thenReturn(600.0);
        assertThat(dashRepo.maxDurationSeconds("1001", from(), to())).isEqualTo(600.0);
    }

    @Test
    void minDurationSeconds_delegatesToRepo() {
        when(repo.minDurationSeconds("1001", from(), to())).thenReturn(30.0);
        assertThat(dashRepo.minDurationSeconds("1001", from(), to())).isEqualTo(30.0);
    }

    @Test
    void countShortCalls_delegatesToRepo() {
        when(repo.countShortCalls("1001", from(), to())).thenReturn(3L);
        assertThat(dashRepo.countShortCalls("1001", from(), to())).isEqualTo(3L);
    }

    @Test
    void countLongCalls_delegatesToRepo() {
        when(repo.countLongCalls("1001", from(), to())).thenReturn(1L);
        assertThat(dashRepo.countLongCalls("1001", from(), to())).isEqualTo(1L);
    }

    @Test
    void countByHour_returnsRawRows() {
        Object[] row = new Object[]{10, 3L};
        when(repo.countByHour("1001", from(), to())).thenReturn(List.<Object[]>of(row));
        assertThat(dashRepo.countByHour("1001", from(), to())).hasSize(1);
    }

    @Test
    void countByDay_returnsRawRows() {
        Object[] row = new Object[]{"2026-05-01", 5L};
        when(repo.countByDay("1001", from(), to())).thenReturn(List.<Object[]>of(row));
        assertThat(dashRepo.countByDay("1001", from(), to())).hasSize(1);
    }

    @Test
    void countByDayOfWeek_returnsRawRows() {
        Object[] row = new Object[]{1, 2L};
        when(repo.countByDayOfWeek("1001", from(), to())).thenReturn(List.<Object[]>of(row));
        assertThat(dashRepo.countByDayOfWeek("1001", from(), to())).hasSize(1);
    }

    @Test
    void findActiveExtensions_delegatesToRepo() {
        when(repo.findActiveExtensions(List.of("1001", "1002")))
                .thenReturn(List.of("1001"));
        assertThat(dashRepo.findActiveExtensions(List.of("1001", "1002")))
                .containsExactly("1001");
    }

    // ─── findLastEventByExtension ─────────────────────────────────────────

    @Test
    void findLastEventByExtension_found_returnsMappedDomain() {
        CallEventEntity entity = CallEventEntity.builder()
                .id(1L).callId("CALL-001").callerExtension("1001").build();
        when(repo.findLastEventByExtension("1001")).thenReturn(Optional.of(entity));

        Optional<CallEvent> result = dashRepo.findLastEventByExtension("1001");

        assertThat(result).isPresent();
        assertThat(result.get().getCallId()).isEqualTo("CALL-001");
    }

    @Test
    void findLastEventByExtension_notFound_returnsEmpty() {
        when(repo.findLastEventByExtension("9999")).thenReturn(Optional.empty());
        assertThat(dashRepo.findLastEventByExtension("9999")).isEmpty();
    }

    // ─── findRecentEvents ─────────────────────────────────────────────────

    @Test
    void findRecentEvents_returnsMappedList() {
        CallEventEntity entity = CallEventEntity.builder()
                .id(1L).callId("CALL-001").callerExtension("1001").build();
        when(repo.findRecentEvents(any(), any())).thenReturn(List.of(entity));

        List<CallEvent> result = dashRepo.findRecentEvents(List.of("1001"), 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCallId()).isEqualTo("CALL-001");
    }

    // ─── findByCallerExtension ────────────────────────────────────────────

    @Test
    void findByCallerExtension_returnsMappedList() {
        CallEventEntity entity = CallEventEntity.builder()
                .id(2L).callId("CALL-002").callerExtension("1001").build();
        when(repo.findByCallerExtension("1001")).thenReturn(List.of(entity));

        List<CallEvent> result = dashRepo.findByCallerExtension("1001");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCallId()).isEqualTo("CALL-002");
    }

    // ─── getCallSummaryByExtensions ────────────────────────────────────────

    @Test
    void getCallSummaryByExtensions_returnsRawRows() {
        Object[] row = new Object[]{"1001", 5L, 4L};
        when(repo.getCallSummaryByExtensions(any(), any(), any()))
                .thenReturn(List.<Object[]>of(row));

        List<Object[]> result = dashRepo.getCallSummaryByExtensions(List.of("1001"), from(), to());

        assertThat(result).hasSize(1);
    }

    @Test
    void countByDayAndExtension_returnsRawRows() {
        Object[] row = new Object[]{"2026-05-01", "1001", 3L};
        when(repo.countByDayAndExtension(any(), any(), any()))
                .thenReturn(List.<Object[]>of(row));

        List<Object[]> result = dashRepo.countByDayAndExtension(List.of("1001"), from(), to());

        assertThat(result).hasSize(1);
    }

    @Test
    void sumDurationByExtensions_delegatesToRepo() {
        when(repo.sumDurationByExtensions(any(), any(), any())).thenReturn(1800.0);

        Double result = dashRepo.sumDurationByExtensions(List.of("1001"), from(), to());

        assertThat(result).isEqualTo(1800.0);
    }

    @Test
    void findLongActiveCalls_returnsCallIdList() {
        when(repo.findLongActiveCalls(any(), eq(300L))).thenReturn(List.of("CALL-001"));

        List<String> result = dashRepo.findLongActiveCalls(List.of("1001"), 300L);

        assertThat(result).containsExactly("CALL-001");
    }

    @Test
    void findInactiveExtensions_returnsExtensionList() {
        when(repo.findInactiveExtensions(any(), any())).thenReturn(List.of("1002"));

        List<String> result = dashRepo.findInactiveExtensions(List.of("1001", "1002"), from());

        assertThat(result).containsExactly("1002");
    }
}
