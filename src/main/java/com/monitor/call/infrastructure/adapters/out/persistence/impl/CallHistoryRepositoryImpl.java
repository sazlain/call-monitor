package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.ports.out.CallHistoryRepositoryPort;
import com.monitor.call.domain.responses.CallHistoryPage;
import com.monitor.call.domain.responses.CallHistoryResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallEventJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
                .createdAt(toIsoString(r[8]))
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

    /** Convierte cualquier tipo de fecha JDBC a ISO-8601 string para el frontend */
    private String toIsoString(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime odt)
            return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        if (o instanceof Timestamp ts)
            return ts.toInstant().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        // Fallback: el driver devuelve el valor como String (ej. "2024-05-13 12:30:00+00")
        // lo normalizamos a ISO reemplazando el espacio por T
        String s = o.toString();
        if (s.contains(" ") && !s.startsWith("["))
            s = s.replace(" ", "T");
        return s;
    }
}
