package com.monitor.call.domain.usecases;

import com.monitor.call.domain.exceptions.BusinessRuleException;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.in.ReportUseCases;
import com.monitor.call.domain.ports.out.*;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportImpl implements ReportUseCases {

    private static final Logger logger = LoggerFactory.getLogger(ReportImpl.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DashboardRepositoryPort callEventPort;
    private final CallTypificationRepositoryPort typPort;
    private final AgentRepositoryPort agentPort;
    private final AgentGroupRepositoryPort groupPort;
    private final UserRepositoryPort userPort;
    private final LeadRepositoryPort leadPort;

    public ReportImpl(DashboardRepositoryPort callEventPort,
                      CallTypificationRepositoryPort typPort,
                      AgentRepositoryPort agentPort,
                      AgentGroupRepositoryPort groupPort,
                      UserRepositoryPort userPort,
                      LeadRepositoryPort leadPort) {
        this.callEventPort = callEventPort;
        this.typPort       = typPort;
        this.agentPort     = agentPort;
        this.groupPort     = groupPort;
        this.userPort      = userPort;
        this.leadPort      = leadPort;
    }

    // ── Reporte de llamadas por agente ────────────────────────────────────────

    @Override
    public byte[] generateAgentCallReport(String extension, OffsetDateTime from, OffsetDateTime to) {
        logger.info("Reporte llamadas: extension={} from={} to={}", extension, from, to);

        List<CallEvent> events = callEventPort.findByCallerExtension(extension).stream()
                .filter(e -> !e.getCreatedAt().isBefore(from) && !e.getCreatedAt().isAfter(to))
                .toList();

        Map<String, List<CallEvent>> byCall = events.stream()
                .collect(Collectors.groupingBy(CallEvent::getCallId));

        Long agentId = agentPort.findByExtension(extension).map(a -> a.getId()).orElse(-1L);
        List<CallTypification> typifications = typPort.findByAgentAndPeriod(agentId, from, to);
        Map<String, CallTypification> typMap = typifications.stream()
                .collect(Collectors.toMap(CallTypification::getCallId, t -> t, (a, b) -> b));

        String[] header = {
            "Fecha", "Call ID", "Numero marcado", "Nombre contacto",
            "Duracion (seg)", "Estado final", "Flujo", "Tipificado",
            "Resultado", "Nombre tipificacion", "Telefono tipificacion", "Notas"
        };

        return writeCsv(header, writer -> {
            for (Map.Entry<String, List<CallEvent>> entry : byCall.entrySet()) {
                String callId = entry.getKey();
                List<CallEvent> callEvents = entry.getValue().stream()
                        .sorted(Comparator.comparing(CallEvent::getCreatedAt)).toList();

                CallEvent first = callEvents.get(0);
                CallEvent last  = callEvents.get(callEvents.size() - 1);

                long durationSec = 0;
                Optional<CallEvent> answerEvent = callEvents.stream()
                        .filter(e -> "ANSWER".equals(e.getCallStatus().name())).findFirst();
                Optional<CallEvent> hangupEvent = callEvents.stream()
                        .filter(e -> "HANGUP".equals(e.getCallStatus().name())).findFirst();
                if (answerEvent.isPresent() && hangupEvent.isPresent()) {
                    durationSec = java.time.Duration.between(
                            answerEvent.get().getCreatedAt(),
                            hangupEvent.get().getCreatedAt()).getSeconds();
                }

                CallTypification typ = typMap.get(callId);
                boolean typified = typ != null;

                writer.writeNext(new String[]{
                    first.getCreatedAt().format(DT_FMT),
                    callId,
                    safe(first.getCalledNumber()),
                    safe(first.getCallerIdName()),
                    String.valueOf(durationSec),
                    last.getCallStatus().name(),
                    first.getCallFlow() != null ? first.getCallFlow().name() : "",
                    typified ? "Si" : "No",
                    typified ? typ.getResult().name() : "",
                    typified ? safe(typ.getContactName()) : "",
                    typified ? safe(typ.getContactPhone()) : "",
                    typified ? safe(typ.getNotes()) : ""
                });
            }
        });
    }

    // ── Reporte comparativo de grupo ──────────────────────────────────────────

    @Override
    public byte[] generateGroupReport(Long adminId, Long groupId, OffsetDateTime from, OffsetDateTime to) {
        logger.info("Reporte grupo: adminId={} groupId={}", adminId, groupId);

        var groups = groupId != null
                ? groupPort.findById(groupId).map(List::of).orElse(List.of())
                : groupPort.findByAdminId(adminId).stream().filter(g -> Boolean.TRUE.equals(g.getActive())).toList();

        String[] header = {
            "Agente", "Extension", "Grupo",
            "Total llamadas", "Contestadas", "No contestadas",
            "Tasa contacto (%)", "Duracion total (seg)", "Duracion promedio (seg)",
            "Ventas", "Tasa conversion (%)"
        };

        return writeCsv(header, writer -> {
            for (var group : groups) {
                var agents = agentPort.findByGroupId(group.getId()).stream()
                        .filter(a -> Boolean.TRUE.equals(a.getActive())).toList();
                for (var agent : agents) {
                    String name = userPort.findById(agent.getUserId()).map(u -> u.getName()).orElse(agent.getExtension());
                    String ext  = agent.getExtension();

                    List<CallEvent> agentEvents = callEventPort.findByCallerExtension(ext).stream()
                            .filter(e -> !e.getCreatedAt().isBefore(from) && !e.getCreatedAt().isAfter(to))
                            .toList();

                    Map<String, List<CallEvent>> byCall = agentEvents.stream()
                            .collect(Collectors.groupingBy(CallEvent::getCallId));

                    long total = byCall.values().stream()
                            .filter(evts -> evts.stream().anyMatch(e -> "CALLING".equals(e.getCallStatus().name())))
                            .count();
                    long answered = byCall.values().stream()
                            .filter(evts -> evts.stream().anyMatch(e -> "ANSWER".equals(e.getCallStatus().name())))
                            .count();
                    long missed   = total - answered;
                    double rate   = total > 0 ? round1(answered * 100.0 / total) : 0;

                    long totalDur = byCall.values().stream().mapToLong(evts -> {
                        Optional<CallEvent> ans = evts.stream()
                                .filter(e -> "ANSWER".equals(e.getCallStatus().name())).findFirst();
                        Optional<CallEvent> hng = evts.stream()
                                .filter(e -> "HANGUP".equals(e.getCallStatus().name())).findFirst();
                        return (ans.isPresent() && hng.isPresent())
                                ? java.time.Duration.between(ans.get().getCreatedAt(), hng.get().getCreatedAt()).getSeconds()
                                : 0L;
                    }).sum();
                    double avgDur = answered > 0 ? round1(totalDur * 1.0 / answered) : 0;

                    List<CallTypification> typs = typPort.findByAgentAndPeriod(agent.getId(), from, to);
                    long sales    = typs.stream().filter(t -> "SALE".equals(t.getResult().name())).count();
                    double convRate = answered > 0 ? round1(sales * 100.0 / answered) : 0;

                    writer.writeNext(new String[]{
                        name, ext, group.getName(),
                        String.valueOf(total), String.valueOf(answered), String.valueOf(missed),
                        String.valueOf(rate), String.valueOf(totalDur), String.valueOf(avgDur),
                        String.valueOf(sales), String.valueOf(convRate)
                    });
                }
            }
        });
    }

    // ── Reporte de leads ──────────────────────────────────────────────────────

    @Override
    public byte[] generateLeadReport(Long ownerId, OffsetDateTime from, OffsetDateTime to) {
        logger.info("Reporte leads: ownerId={}", ownerId);

        LocalDate fromDate = from.toLocalDate();
        LocalDate toDate   = to.toLocalDate();

        List<Lead> leads = leadPort.findByOwnerId(ownerId).stream()
                .filter(l -> !l.getLeadDate().isBefore(fromDate) && !l.getLeadDate().isAfter(toDate))
                .sorted(Comparator.comparing(Lead::getLeadDate).reversed())
                .toList();

        Map<Long, String> agentNames = new HashMap<>();
        leads.stream().filter(l -> l.getAssignedAgentId() != null)
                .map(Lead::getAssignedAgentId).distinct().forEach(agId ->
                    agentNames.put(agId, agentPort.findById(agId)
                            .flatMap(a -> userPort.findById(a.getUserId()))
                            .map(u -> u.getName()).orElse("Desconocido")));

        Map<Long, List<CallTypification>> typByLead = new HashMap<>();
        leads.forEach(l -> {
            List<CallTypification> typs = typPort.findByLeadId(l.getId());
            if (!typs.isEmpty()) typByLead.put(l.getId(), typs);
        });

        String[] header = {
            "Fecha lead", "Contacto", "Telefono", "Origen",
            "Estado", "Agente asignado", "Tipificado",
            "Ultimo resultado", "Ultima llamada", "Fecha callback", "Notas"
        };

        return writeCsv(header, writer -> {
            for (Lead lead : leads) {
                String assignedName = lead.getAssignedAgentId() != null
                        ? agentNames.getOrDefault(lead.getAssignedAgentId(), "Sin asignar")
                        : "Sin asignar";

                List<CallTypification> typs = typByLead.getOrDefault(lead.getId(), List.of());
                CallTypification lastTyp = typs.isEmpty() ? null : typs.get(typs.size() - 1);

                writer.writeNext(new String[]{
                    lead.getLeadDate().format(D_FMT),
                    safe(lead.getContactName()),
                    safe(lead.getContactPhone()),
                    safe(lead.getLeadSource()),
                    lead.getStatus().name(),
                    assignedName,
                    lastTyp != null ? "Si" : "No",
                    lastTyp != null ? lastTyp.getResult().name() : "",
                    lastTyp != null ? lastTyp.getCreatedAt().format(DT_FMT) : "",
                    lead.getCallbackDate() != null ? lead.getCallbackDate().format(D_FMT) : "",
                    safe(lead.getNotes())
                });
            }
        });
    }

    // ── Reporte de callbacks ──────────────────────────────────────────────────

    @Override
    public byte[] generateCallbackReport(Long userId, OffsetDateTime from, OffsetDateTime to) {
        logger.info("Reporte callbacks: userId={}", userId);

        LocalDate today = LocalDate.now();
        Long agentId = agentPort.findByUserId(userId).map(a -> a.getId()).orElse(-1L);
        List<Lead> callbacks = leadPort.findPendingCallbacks(userId, agentId);

        String[] header = {
            "Contacto", "Telefono", "Origen", "Agente asignado",
            "Fecha callback", "Estado", "Vencido", "Dias vencimiento", "Notas"
        };

        return writeCsv(header, writer -> {
            for (Lead lead : callbacks) {
                String assignedName = lead.getAssignedAgentId() != null
                        ? agentPort.findById(lead.getAssignedAgentId())
                            .flatMap(a -> userPort.findById(a.getUserId()))
                            .map(u -> u.getName()).orElse("Sin asignar")
                        : "Sin asignar";

                boolean overdue = lead.getCallbackDate() != null && lead.getCallbackDate().isBefore(today);
                long daysDiff = lead.getCallbackDate() != null
                        ? ChronoUnit.DAYS.between(today, lead.getCallbackDate())
                        : 0;

                writer.writeNext(new String[]{
                    safe(lead.getContactName()),
                    safe(lead.getContactPhone()),
                    safe(lead.getLeadSource()),
                    assignedName,
                    lead.getCallbackDate() != null ? lead.getCallbackDate().format(D_FMT) : "",
                    lead.getStatus().name(),
                    overdue ? "Si" : "No",
                    String.valueOf(Math.abs(daysDiff)),
                    safe(lead.getNotes())
                });
            }
        });
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface CsvRowWriter {
        void write(CSVWriter writer) throws Exception;
    }

    private byte[] writeCsv(String[] header, CsvRowWriter rowWriter) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                writer.writeNext(header);
                rowWriter.write(writer);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new BusinessRuleException("Error generando CSV: " + e.getMessage(), e);
        }
    }

    private String safe(String value) { return value != null ? value : ""; }

    private double round1(double value) { return Math.round(value * 10.0) / 10.0; }
}
