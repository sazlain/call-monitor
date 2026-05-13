package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.ports.out.CallHistoryRepositoryPort;
import com.monitor.call.domain.responses.CallHistoryPage;
import com.monitor.call.domain.responses.CallHistoryResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class CallHistoryRepositoryImpl implements CallHistoryRepositoryPort {

    private final CallEventJpaRepository repo;

    public CallHistoryRepositoryImpl(CallEventJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public CallHistoryPage findHistory(String extension, String status,
                                       OffsetDateTime from, OffsetDateTime to,
                                       int page, int size) {
        Page<Object[]> result = repo.findHistory(
                emptyToNull(extension), emptyToNull(status),
                from, to,
                PageRequest.of(page, size));

        List<CallHistoryResponse> content = result.getContent().stream()
                .map(this::mapRow)
                .toList();

        return CallHistoryPage.builder()
                .content(content)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .page(page)
                .size(size)
                .build();
    }

    private CallHistoryResponse mapRow(Object[] r) {
        return CallHistoryResponse.builder()
                .id(toLong(r[0]))
                .callId(str(r[1]))
                .callerExtension(str(r[2]))
                .callerIdNum(str(r[3]))
                .callerIdName(str(r[4]))
                .calledNumber(str(r[5]))
                .callStatus(str(r[6]))
                .callFlow(str(r[7]))
                .createdAt(toOdt(r[8]))
                .agentId(toLong(r[9]))
                .agentName(str(r[10]))
                .agentExtension(str(r[11]))
                .typificationResult(str(r[12]))
                .typificationNotes(str(r[13]))
                .callbackDate(str(r[14]))
                .leadId(toLong(r[15]))
                .leadContactName(str(r[16]))
                .leadContactPhone(str(r[17]))
                .build();
    }

    private String str(Object o)  { return o != null ? o.toString() : null; }
    private Long toLong(Object o) { return o instanceof Number n ? n.longValue() : null; }
    private String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CallHistoryRepositoryImpl.class);

    private OffsetDateTime toOdt(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime odt) return odt;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (o instanceof java.time.LocalDateTime ldt) return ldt.atOffset(ZoneOffset.UTC);
        if (o instanceof java.time.Instant inst) return inst.atOffset(ZoneOffset.UTC);
        log.warn("createdAt tipo inesperado: {} = {}", o.getClass().getName(), o);
        return null;
    }
}
