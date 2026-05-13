package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.out.DashboardRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Component
public class DashboardRepositoryImpl implements DashboardRepositoryPort {

    private final CallEventJpaRepository repo;

    public DashboardRepositoryImpl(CallEventJpaRepository repo) { this.repo = repo; }

    @Override public long countTotalCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countTotalCalls(ext, from, to); }
    @Override public long countAnsweredCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countAnsweredCalls(ext, from, to); }
    @Override public long countMissedCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countMissedCalls(ext, from, to); }
    @Override public long countOutboundCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countOutboundCalls(ext, from, to); }
    @Override public long countInboundCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countInboundCalls(ext, from, to); }
    @Override public Double sumDurationSeconds(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.sumDurationSeconds(ext, from, to); }
    @Override public Double maxDurationSeconds(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.maxDurationSeconds(ext, from, to); }
    @Override public Double minDurationSeconds(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.minDurationSeconds(ext, from, to); }
    @Override public long countCompletedCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countCompletedCalls(ext, from, to); }
    @Override public long countShortCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countShortCalls(ext, from, to); }
    @Override public long countLongCalls(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countLongCalls(ext, from, to); }
    @Override public List<Object[]> countByHour(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countByHour(ext, from, to); }
    @Override public List<Object[]> countByDay(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countByDay(ext, from, to); }
    @Override public List<Object[]> countByDayOfWeek(String ext, OffsetDateTime from, OffsetDateTime to) { return repo.countByDayOfWeek(ext, from, to); }
    @Override public List<String> findActiveExtensions(List<String> extensions) { return repo.findActiveExtensions(extensions); }

    @Override
    public Optional<CallEvent> findLastEventByExtension(String ext) {
        return repo.findLastEventByExtension(ext).map(CallEventMapper::entityToDomain);
    }

    @Override
    public List<CallEvent> findRecentEvents(List<String> extensions, int limit) {
        return repo.findRecentEvents(extensions, PageRequest.of(0, limit))
                .stream().map(this::recentRowToDomain).toList();
    }

    /**
     * Mapea la fila Object[] devuelta por findRecentEvents al modelo de dominio.
     * Columnas: [0]=id, [1]=call_id, [2]=caller_id_num, [3]=caller_id_name,
     *           [4]=called_number, [5]=call_status, [6]=call_flow,
     *           [7]=caller_extension, [8]=created_at, [9]=duration_seconds
     */
    private CallEvent recentRowToDomain(Object[] row) {
        return CallEvent.builder()
                .id(row[0] != null ? ((Number) row[0]).longValue() : null)
                .callId(row[1] != null ? row[1].toString() : null)
                .callerIdNum(row[2] != null ? row[2].toString() : null)
                .callerIdName(row[3] != null ? row[3].toString() : null)
                .calledNumber(row[4] != null ? row[4].toString() : null)
                .callStatus(row[5] != null ? CallStatus.valueOf(row[5].toString()) : null)
                .callFlow(row[6] != null ? CallFlow.valueOf(row[6].toString()) : null)
                .callerExtension(row[7] != null ? row[7].toString() : null)
                .createdAt(row[8] != null ? toOdt(row[8]) : null)
                .durationSeconds(row[9] != null ? ((Number) row[9]).longValue() : null)
                .build();
    }

    private OffsetDateTime toOdt(Object o) {
        if (o instanceof OffsetDateTime odt) return odt;
        if (o instanceof java.time.Instant inst) return inst.atOffset(ZoneOffset.UTC);
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (o instanceof java.time.LocalDateTime ldt) return ldt.atOffset(ZoneOffset.UTC);
        return null;
    }

    @Override
    public List<CallEvent> findByCallerExtension(String ext) {
        return repo.findByCallerExtension(ext)
                .stream().map(CallEventMapper::entityToDomain).toList();
    }

    @Override public List<Object[]> getCallSummaryByExtensions(List<String> extensions, OffsetDateTime from, OffsetDateTime to) { return repo.getCallSummaryByExtensions(extensions, from, to); }
    @Override public List<Object[]> countByDayAndExtension(List<String> extensions, OffsetDateTime from, OffsetDateTime to) { return repo.countByDayAndExtension(extensions, from, to); }
    @Override public Double sumDurationByExtensions(List<String> extensions, OffsetDateTime from, OffsetDateTime to) { return repo.sumDurationByExtensions(extensions, from, to); }
    @Override public List<String> findLongActiveCalls(List<String> extensions, long thresholdSeconds) { return repo.findLongActiveCalls(extensions, thresholdSeconds); }
    @Override public List<String> findInactiveExtensions(List<String> extensions, OffsetDateTime since) { return repo.findInactiveExtensions(extensions, since); }

    @Override
    public List<Object[]> findDailyActivitySummary(List<String> extensions, OffsetDateTime from, OffsetDateTime to) {
        return repo.findDailyActivitySummary(extensions, from, to);
    }
}
