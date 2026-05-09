package com.monitor.call.domain.ports.out;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DashboardRepositoryPort {
    long countTotalCalls(String ext, OffsetDateTime from, OffsetDateTime to);
    long countAnsweredCalls(String ext, OffsetDateTime from, OffsetDateTime to);
    long countMissedCalls(String ext, OffsetDateTime from, OffsetDateTime to);
    long countOutboundCalls(String ext, OffsetDateTime from, OffsetDateTime to);
    long countInboundCalls(String ext, OffsetDateTime from, OffsetDateTime to);
    Double sumDurationSeconds(String ext, OffsetDateTime from, OffsetDateTime to);
    Double maxDurationSeconds(String ext, OffsetDateTime from, OffsetDateTime to);
    Double minDurationSeconds(String ext, OffsetDateTime from, OffsetDateTime to);
    long countShortCalls(String ext, OffsetDateTime from, OffsetDateTime to);
    long countLongCalls(String ext, OffsetDateTime from, OffsetDateTime to);
    List<Object[]> countByHour(String ext, OffsetDateTime from, OffsetDateTime to);
    List<Object[]> countByDay(String ext, OffsetDateTime from, OffsetDateTime to);
    List<Object[]> countByDayOfWeek(String ext, OffsetDateTime from, OffsetDateTime to);
    List<String> findActiveExtensions(List<String> extensions);
    Optional<CallEventEntity> findLastEventByExtension(String ext);
    List<CallEventEntity> findRecentEvents(List<String> extensions, int limit);
    List<Object[]> getCallSummaryByExtensions(List<String> extensions, OffsetDateTime from, OffsetDateTime to);
    List<Object[]> countByDayAndExtension(List<String> extensions, OffsetDateTime from, OffsetDateTime to);
    Double sumDurationByExtensions(List<String> extensions, OffsetDateTime from, OffsetDateTime to);
    List<String> findLongActiveCalls(List<String> extensions, long thresholdSeconds);
    List<String> findInactiveExtensions(List<String> extensions, OffsetDateTime since);
}
