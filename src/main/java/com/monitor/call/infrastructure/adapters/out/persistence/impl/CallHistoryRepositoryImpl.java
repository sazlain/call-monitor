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
        // r[0]=id  r[1]=call_id  r[2]=call_api_id  r[3]=caller_extension  r[4]=caller_id_num
        // r[5]=caller_id_name  r[6]=called_number  r[7]=call_status(outcome)  r[8]=call_flow  r[9]=created_at
        // r[10]=duration_seconds  r[11]=agent_id  r[12]=agent_name  r[13]=agent_extension
        // r[14]=typification_result  r[15]=typification_notes  r[16]=callback_date
        // r[17]=lead_id  r[18]=lead_contact_name  r[19]=lead_contact_phone
        return CallHistoryResponse.builder()
                .id(toLong(r[0]))
                .callId(str(r[1]))
                .callApiId(str(r[2]))
                .callerExtension(str(r[3]))
                .callerIdNum(str(r[4]))
                .callerIdName(str(r[5]))
                .calledNumber(str(r[6]))
                .callStatus(str(r[7]))
                .callFlow(str(r[8]))
                .createdAt(toIsoString(r[9]))
                .durationSeconds(toInt(r[10]))
                .agentId(toLong(r[11]))
                .agentName(str(r[12]))
                .agentExtension(str(r[13]))
                .typificationResult(str(r[14]))
                .typificationNotes(str(r[15]))
                .callbackDate(str(r[16]))
                .leadId(toLong(r[17]))
                .leadContactName(str(r[18]))
                .leadContactPhone(str(r[19]))
                .build();
    }

    private String str(Object o)     { return o != null ? o.toString() : null; }
    private Long toLong(Object o)    { return o instanceof Number n ? n.longValue() : null; }
    private Integer toInt(Object o)  { return o instanceof Number n ? n.intValue() : null; }
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
