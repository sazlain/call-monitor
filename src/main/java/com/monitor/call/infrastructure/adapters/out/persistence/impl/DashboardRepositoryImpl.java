package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.out.DashboardRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
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
                .stream().map(CallEventMapper::entityToDomain).toList();
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
