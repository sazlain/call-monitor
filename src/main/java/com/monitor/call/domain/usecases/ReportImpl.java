package com.monitor.call.domain.usecases;

import com.monitor.call.domain.ports.in.ReportUseCases;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallTypificationEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.*;
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

    private final CallEventJpaRepository callEventRepo;
    private final CallTypificationJpaRepository typRepo;
    private final AgentJpaRepository agentRepo;
    private final AgentGroupJpaRepository groupRepo;
    private final UserJpaRepository userRepo;
    private final LeadJpaRepository leadRepo;

    public ReportImpl(CallEventJpaRepository callEventRepo,
                      CallTypificationJpaRepository typRepo,
                      AgentJpaRepository agentRepo,
                      AgentGroupJpaRepository groupRepo,
                      UserJpaRepository userRepo,
                      LeadJpaRepository leadRepo) {
        this.callEventRepo = callEventRepo;
        this.typRepo       = typRepo;
        this.agentRepo     = agentRepo;
        this.groupRepo     = groupRepo;
        this.userRepo      = userRepo;
        this.leadRepo      = leadRepo;
    }

    // ── Reporte de llamadas por agente ────────────────────────────────────────

    @Override
    public byte[] generateAgentCallReport(String extension, OffsetDateTime from, OffsetDateTime to) {
        logger.info("Reporte llamadas: extension={} from={} to={}", extension, from, to);

        List<CallEventEntity> events = callEventRepo.findByCallerExtension(extension).stream()
                .filter(e -> !e.getCreatedAt().isBefore(from) && !e.getCreatedAt().isAfter(to))
                .toList();

        Map<String, List<CallEventEntity>> byCall = events.stream()
                .collect(Collectors.groupingBy(CallEventEntity::getCallId));

        Long agentId = agentRepo.findByExtension(extension).map(a -> a.getId()).orElse(-1L);
        List<CallTypificationEntity> typifications = typRepo.findByAgentAndPeriod(agentId, from, to);
        Map<String, CallTypificationEntity> typMap = typifications.stream()
                .collect(Collectors.toMap(CallTypificationEntity::getCallId, t -> t, (a, b) -> b));

        String[] header = {
            "Fecha", "Call ID", "Numero marcado", "Nombre contacto",
            "Duracion (seg)", "Estado final", "Flujo", "Tipificado",
            "Resultado", "Nombre tipificacion", "Telefono tipificacion", "Notas"
        };

        return writeCsv(header, writer -> {
            for (Map.Entry<String, List<CallEventEntity>> entry : byCall.entrySet()) {
                String callId = entry.getKey();
                List<CallEventEntity> callEvents = entry.getValue().stream()
                        .sorted(Comparator.comparing(CallEventEntity::getCreatedAt)).toList();

                CallEventEntity first = callEvents.get(0);
                CallEventEntity last  = callEvents.get(callEvents.size() - 1);

                long durationSec = 0;
                Optional<CallEventEntity> answerEvent = callEvents.stream()
                        .filter(e -> "ANSWER".equals(e.getCallStatus().name())).findFirst();
                Optional<CallEventEntity> hangupEvent = callEvents.stream()
                        .filter(e -> "HANGUP".equals(e.getCallStatus().name())).findFirst();
                if (answerEvent.isPresent() && hangupEvent.isPresent()) {
                    durationSec = java.time.Duration.between(
                            answerEvent.get().getCreatedAt(),
                            hangupEvent.get().getCreatedAt()).getSeconds();
                }

                CallTypificationEntity typ = typMap.get(callId);
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
                ? groupRepo.findById(groupId).map(List::of).orElse(List.of())
                : groupRepo.findByAdminIdAndActiveTrue(adminId);

        String[] header = {
            "Agente", "Extension", "Grupo",
            "Total llamadas", "Contestadas", "No contestadas",
            "Tasa contacto (%)", "Duracion total (seg)", "Duracion promedio (seg)",
            "Ventas", "Tasa conversion (%)"
        };

        return writeCsv(header, writer -> {
            for (var group : groups) {
                var agents = agentRepo.findByGroupIdAndActiveTrue(group.getId());
                for (var agent : agents) {
                    var user = userRepo.findById(agent.getUserId()).orElse(null);
                    String name = user != null ? user.getName() : agent.getExtension();
                    String ext  = agent.getExtension();

                    List<CallEventEntity> agentEvents = callEventRepo.findByCallerExtension(ext).stream()
                            .filter(e -> !e.getCreatedAt().isBefore(from) && !e.getCreatedAt().isAfter(to))
                            .toList();

                    Map<String, List<CallEventEntity>> byCall = agentEvents.stream()
                            .collect(Collectors.groupingBy(CallEventEntity::getCallId));

                    long total = byCall.values().stream()
                            .filter(evts -> evts.stream().anyMatch(e -> "CALLING".equals(e.getCallStatus().name())))
                            .count();
                    long answered = byCall.values().stream()
                            .filter(evts -> evts.stream().anyMatch(e -> "ANSWER".equals(e.getCallStatus().name())))
                            .count();
                    long missed   = total - answered;
                    double rate   = total > 0 ? round1(answered * 100.0 / total) : 0;

                    long totalDur = byCall.values().stream().mapToLong(evts -> {
                        Optional<CallEventEntity> ans = evts.stream()
                                .filter(e -> "ANSWER".equals(e.getCallStatus().name())).findFirst();
                        Optional<CallEventEntity> hng = evts.stream()
                                .filter(e -> "HANGUP".equals(e.getCallStatus().name())).findFirst();
                        return (ans.isPresent() && hng.isPresent())
                                ? java.time.Duration.between(ans.get().getCreatedAt(), hng.get().getCreatedAt()).getSeconds()
                                : 0L;
                    }).sum();
                    double avgDur = answered > 0 ? round1(totalDur * 1.0 / answered) : 0;

                    List<CallTypificationEntity> typs = typRepo.findByAgentAndPeriod(agent.getId(), from, to);
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

        List<LeadEntity> leads = leadRepo.findByOwnerId(ownerId).stream()
                .filter(l -> !l.getLeadDate().isBefore(fromDate) && !l.getLeadDate().isAfter(toDate))
                .sorted(Comparator.comparing(LeadEntity::getLeadDate).reversed())
                .toList();

        // Precargar nombres de agentes asignados
        Map<Long, String> agentNames = new HashMap<>();
        leads.stream().filter(l -> l.getAssignedAgentId() != null)
                .map(LeadEntity::getAssignedAgentId).distinct().forEach(agId ->
                    agentNames.put(agId, agentRepo.findById(agId)
                            .flatMap(a -> userRepo.findById(a.getUserId()))
                            .map(u -> u.getName()).orElse("Desconocido")));

        // Precargar tipificaciones por leadId
        Map<Long, List<CallTypificationEntity>> typByLead = new HashMap<>();
        leads.forEach(l -> {
            List<CallTypificationEntity> typs = typRepo.findByLeadId(l.getId());
            if (!typs.isEmpty()) typByLead.put(l.getId(), typs);
        });

        String[] header = {
            "Fecha lead", "Contacto", "Telefono", "Origen",
            "Estado", "Agente asignado", "Tipificado",
            "Ultimo resultado", "Ultima llamada", "Fecha callback", "Notas"
        };

        return writeCsv(header, writer -> {
            for (LeadEntity lead : leads) {
                String assignedName = lead.getAssignedAgentId() != null
                        ? agentNames.getOrDefault(lead.getAssignedAgentId(), "Sin asignar")
                        : "Sin asignar";

                List<CallTypificationEntity> typs = typByLead.getOrDefault(lead.getId(), List.of());
                CallTypificationEntity lastTyp = typs.isEmpty() ? null : typs.get(typs.size() - 1);

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
        Long agentId = agentRepo.findByUserId(userId).map(a -> a.getId()).orElse(-1L);
        List<LeadEntity> callbacks = leadRepo.findPendingCallbacks(today.plusYears(1), userId, agentId);

        String[] header = {
            "Contacto", "Telefono", "Origen", "Agente asignado",
            "Fecha callback", "Estado", "Vencido", "Dias vencimiento", "Notas"
        };

        return writeCsv(header, writer -> {
            for (LeadEntity lead : callbacks) {
                String assignedName = lead.getAssignedAgentId() != null
                        ? agentRepo.findById(lead.getAssignedAgentId())
                            .flatMap(a -> userRepo.findById(a.getUserId()))
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
            // BOM UTF-8 para que Excel abra correctamente con tildes y caracteres especiales
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
                writer.writeNext(header);
                rowWriter.write(writer);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando CSV: " + e.getMessage(), e);
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
