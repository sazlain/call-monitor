package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.ports.in.DashboardUseCases;
import com.monitor.call.domain.ports.out.DashboardRepositoryPort;
import com.monitor.call.domain.responses.*;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentGroupJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallTypificationJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardImpl implements DashboardUseCases {

    private static final String[] DOW_NAMES = {"Dom","Lun","Mar","Mie","Jue","Vie","Sab"};
    private static final long LONG_CALL_THRESHOLD_SECONDS = 1200; // 20 min
    private static final long INACTIVE_THRESHOLD_MINUTES = 30;

    private final DashboardRepositoryPort dashRepo;
    private final AgentJpaRepository agentRepo;
    private final AgentGroupJpaRepository groupRepo;
    private final UserJpaRepository userRepo;
    private final CallTypificationJpaRepository typRepo;
    private final LeadJpaRepository leadRepo;

    public DashboardImpl(DashboardRepositoryPort dashRepo,
                         AgentJpaRepository agentRepo,
                         AgentGroupJpaRepository groupRepo,
                         UserJpaRepository userRepo,
                         CallTypificationJpaRepository typRepo,
                         LeadJpaRepository leadRepo) {
        this.dashRepo = dashRepo;
        this.agentRepo = agentRepo;
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
        this.typRepo = typRepo;
        this.leadRepo = leadRepo;
    }

    // ── Dashboard del agente ─────────────────────────────────────────────────

    @Override
    public AgentDashboardResponse getAgentDashboard(String extension, OffsetDateTime from, OffsetDateTime to) {
        var agentEntity = agentRepo.findByExtension(extension).orElseThrow(() -> new RuntimeException("Agente no encontrado: " + extension));
        var user = userRepo.findById(agentEntity.getUserId()).orElse(null);
        String agentName = user != null ? user.getName() : extension;

        // Bloque 1: Volumen
        long total = dashRepo.countTotalCalls(extension, from, to);
        long answered = dashRepo.countAnsweredCalls(extension, from, to);
        long missed = dashRepo.countMissedCalls(extension, from, to);
        long outbound = dashRepo.countOutboundCalls(extension, from, to);
        long inbound = dashRepo.countInboundCalls(extension, from, to);
        double answerRate = total > 0 ? Math.round((answered * 100.0 / total) * 10.0) / 10.0 : 0;

        // Bloque 2: Tiempo
        Double totalDur = dashRepo.sumDurationSeconds(extension, from, to);
        long totalDurLong = totalDur != null ? totalDur.longValue() : 0;
        double avgDur = answered > 0 ? Math.round((totalDurLong * 1.0 / answered) * 10.0) / 10.0 : 0;
        Double maxDur = dashRepo.maxDurationSeconds(extension, from, to);
        Double minDur = dashRepo.minDurationSeconds(extension, from, to);
        long shortCalls = dashRepo.countShortCalls(extension, from, to);
        long longCalls = dashRepo.countLongCalls(extension, from, to);

        // Bloque 3: Ritmo (llamadas por hora del período)
        double hours = java.time.Duration.between(from, to).toHours();
        double callsPerHour = hours > 0 ? Math.round((total / hours) * 10.0) / 10.0 : 0;

        // Bloque 4: Tipificación
        long typified = typRepo.findByAgentId(agentEntity.getId()).stream()
                .filter(t -> t.getCreatedAt().isAfter(from) && t.getCreatedAt().isBefore(to))
                .count();
        long untypified = answered - typified;

        List<Object[]> resultRows = typRepo.countByResultForAgent(agentEntity.getId(), from, to);
        List<AgentDashboardResponse.ResultCount> resultDist = resultRows.stream()
                .map(r -> AgentDashboardResponse.ResultCount.builder()
                        .result(r[0].toString()).count(((Number) r[1]).longValue()).build())
                .toList();

        // Bloque 7: Tendencias
        List<AgentDashboardResponse.HourlyCount> byHour = dashRepo.countByHour(extension, from, to).stream()
                .map(r -> AgentDashboardResponse.HourlyCount.builder()
                        .hour(((Number) r[0]).intValue()).count(((Number) r[1]).longValue()).build())
                .toList();

        List<AgentDashboardResponse.DailyCount> byDay = dashRepo.countByDay(extension, from, to).stream()
                .map(r -> AgentDashboardResponse.DailyCount.builder()
                        .date(r[0].toString()).count(((Number) r[1]).longValue()).build())
                .toList();

        List<AgentDashboardResponse.DowCount> byDow = dashRepo.countByDayOfWeek(extension, from, to).stream()
                .map(r -> { int dow = ((Number) r[0]).intValue();
                    return AgentDashboardResponse.DowCount.builder()
                            .dayOfWeek(dow).dayName(DOW_NAMES[dow % 7])
                            .count(((Number) r[1]).longValue()).build(); })
                .toList();

        // Recientes (últimas 20 llamadas)
        List<RecentCallResponse> recent = dashRepo.findRecentEvents(List.of(extension), 20).stream()
                .map(this::toRecentCall).toList();

        // Estado actual
        boolean isActive = !dashRepo.findActiveExtensions(List.of(extension)).isEmpty();

        return AgentDashboardResponse.builder()
                .extension(extension).agentName(agentName).isActive(isActive)
                .totalCalls(total).answeredCalls(answered).missedCalls(missed)
                .outboundCalls(outbound).inboundCalls(inbound).answerRate(answerRate)
                .totalDurationSeconds(totalDurLong).avgDurationSeconds(avgDur)
                .maxDurationSeconds(maxDur != null ? maxDur.longValue() : 0L)
                .minDurationSeconds(minDur != null ? minDur.longValue() : 0L)
                .shortCalls(shortCalls).longCalls(longCalls)
                .callsPerHour(callsPerHour)
                .typifiedCalls(typified).untypifiedCalls(Math.max(0, untypified))
                .resultDistribution(resultDist)
                .callsByHour(byHour).callsByDay(byDay).callsByDayOfWeek(byDow)
                .recentCalls(recent)
                .build();
    }

    // ── Dashboard del admin ──────────────────────────────────────────────────

    @Override
    public AdminDashboardResponse getAdminDashboard(Long adminId, OffsetDateTime from, OffsetDateTime to, Long groupId) {
        var groups = groupId != null
                ? groupRepo.findById(groupId).map(List::of).orElse(List.of())
                : groupRepo.findByAdminIdAndActiveTrue(adminId);

        var allExtensions = groups.stream()
                .flatMap(g -> agentRepo.findExtensionsByGroupId(g.getId()).stream())
                .toList();

        if (allExtensions.isEmpty()) return AdminDashboardResponse.builder().adminEmail("").build();

        // KPIs globales
        List<Object[]> summary = dashRepo.getCallSummaryByExtensions(allExtensions, from, to);
        long totalCalls = 0, answeredCalls = 0, missedCalls = 0;
        for (Object[] row : summary) {
            totalCalls += ((Number) row[1]).longValue();
            answeredCalls += ((Number) row[2]).longValue();
            missedCalls += ((Number) row[3]).longValue();
        }
        Double totalDur = dashRepo.sumDurationByExtensions(allExtensions, from, to);
        long totalDurLong = totalDur != null ? totalDur.longValue() : 0;
        double answerRate = totalCalls > 0 ? Math.round((answeredCalls * 100.0 / totalCalls) * 10.0) / 10.0 : 0;
        double avgDur = answeredCalls > 0 ? Math.round((totalDurLong * 1.0 / answeredCalls) * 10.0) / 10.0 : 0;

        // Mapa extension -> summary row
        Map<String, Object[]> summaryMap = summary.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> r));

        // Por grupo
        List<AdminDashboardResponse.GroupSummary> groupSummaries = groups.stream().map(g -> {
            var exts = agentRepo.findExtensionsByGroupId(g.getId());
            long gTotal = 0, gAnswered = 0;
            Long gDur = 0L;
            int active = 0;
            for (String e : exts) {
                if (summaryMap.containsKey(e)) {
                    gTotal += ((Number) summaryMap.get(e)[1]).longValue();
                    gAnswered += ((Number) summaryMap.get(e)[2]).longValue();
                }
            }
            var activeExts = dashRepo.findActiveExtensions(exts);
            Double gDurD = exts.isEmpty() ? null : dashRepo.sumDurationByExtensions(exts, from, to);
            double gRate = gTotal > 0 ? Math.round((gAnswered * 100.0 / gTotal) * 10.0) / 10.0 : 0;
            return AdminDashboardResponse.GroupSummary.builder()
                    .groupId(g.getId()).groupName(g.getName())
                    .totalAgents(exts.size()).activeAgents(activeExts.size())
                    .totalCalls(gTotal).answeredCalls(gAnswered).answerRate(gRate)
                    .totalDurationSeconds(gDurD != null ? gDurD.longValue() : 0L)
                    .build();
        }).toList();

        // Ranking de agentes
        List<String> activeExts = dashRepo.findActiveExtensions(allExtensions);
        List<AdminDashboardResponse.AgentSummary> ranking = allExtensions.stream().map(ext -> {
            var agent = agentRepo.findByExtension(ext).orElse(null);
            String name = agent != null ? userRepo.findById(agent.getUserId()).map(u -> u.getName()).orElse(ext) : ext;
            String groupName = (agent != null && agent.getGroup() != null) ? agent.getGroup().getName() : "";
            Object[] row = summaryMap.get(ext);
            long extTotal = row != null ? ((Number) row[1]).longValue() : 0;
            long extAnswered = row != null ? ((Number) row[2]).longValue() : 0;
            Double extDur = dashRepo.sumDurationSeconds(ext, from, to);
            long extDurLong = extDur != null ? extDur.longValue() : 0;
            double extRate = extTotal > 0 ? Math.round((extAnswered * 100.0 / extTotal) * 10.0) / 10.0 : 0;
            double extAvg = extAnswered > 0 ? Math.round((extDurLong * 1.0 / extAnswered) * 10.0) / 10.0 : 0;

            // Tasa de conversion desde tipificaciones
            long agentId = agent != null ? agent.getId() : -1;
            long sales = agentId > 0 ? typRepo.countByResultForAgent(agentId, from, to).stream()
                    .filter(r -> "SALE".equals(r[0].toString())).mapToLong(r -> ((Number)r[1]).longValue()).sum() : 0;
            double convRate = extAnswered > 0 ? Math.round((sales * 100.0 / extAnswered) * 10.0) / 10.0 : 0;

            return AdminDashboardResponse.AgentSummary.builder()
                    .extension(ext).agentName(name).groupName(groupName)
                    .isActive(activeExts.contains(ext))
                    .totalCalls(extTotal).answeredCalls(extAnswered).answerRate(extRate)
                    .totalDurationSeconds(extDurLong).avgDurationSeconds(extAvg).conversionRate(convRate)
                    .build();
        }).sorted(Comparator.comparingLong(AdminDashboardResponse.AgentSummary::getTotalCalls).reversed()).toList();

        // Tendencia diaria
        List<AdminDashboardResponse.DailyTrend> trend = dashRepo.countByDayAndExtension(allExtensions, from, to).stream()
                .map(r -> {
                    String ext = (String) r[1];
                    var agent = agentRepo.findByExtension(ext).orElse(null);
                    String name = agent != null ? userRepo.findById(agent.getUserId()).map(u -> u.getName()).orElse(ext) : ext;
                    return AdminDashboardResponse.DailyTrend.builder()
                            .date(r[0].toString()).extension(ext).agentName(name)
                            .count(((Number) r[2]).longValue()).build();
                }).toList();

        // Alertas (Bloque 9)
        List<String> alerts = new ArrayList<>();
        List<String> longCalls = dashRepo.findLongActiveCalls(allExtensions, LONG_CALL_THRESHOLD_SECONDS);
        if (!longCalls.isEmpty()) alerts.add("Llamadas largas activas (>20min): extensiones " + String.join(", ", longCalls));
        List<String> inactive = dashRepo.findInactiveExtensions(allExtensions, OffsetDateTime.now().minusMinutes(INACTIVE_THRESHOLD_MINUTES));
        if (!inactive.isEmpty()) alerts.add("Agentes inactivos (>30min): extensiones " + String.join(", ", inactive));

        var adminUser = userRepo.findById(adminId).orElse(null);

        return AdminDashboardResponse.builder()
                .adminEmail(adminUser != null ? adminUser.getEmail() : "")
                .totalGroups(groups.size())
                .totalAgents(allExtensions.size())
                .activeAgents(activeExts.size())
                .totalCalls(totalCalls).answeredCalls(answeredCalls).missedCalls(missedCalls)
                .answerRate(answerRate).totalDurationSeconds(totalDurLong).avgDurationSeconds(avgDur)
                .groups(groupSummaries).agentRanking(ranking).dailyTrend(trend).alerts(alerts)
                .build();
    }

    // ── Estado en tiempo real ────────────────────────────────────────────────

    @Override
    public AgentStatusResponse getAgentStatus(Long adminId, Long groupId) {
        List<String> extensions = groupId != null
                ? agentRepo.findExtensionsByGroupId(groupId)
                : agentRepo.findExtensionsByAdminId(adminId);

        List<String> activeExts = dashRepo.findActiveExtensions(extensions);

        List<AgentStatusResponse.AgentCurrentStatus> statuses = extensions.stream().map(ext -> {
            var agent = agentRepo.findByExtension(ext).orElse(null);
            String name = agent != null ? userRepo.findById(agent.getUserId()).map(u -> u.getName()).orElse(ext) : ext;
            String groupName = (agent != null && agent.getGroup() != null) ? agent.getGroup().getName() : "";
            boolean isActive = activeExts.contains(ext);

            CallEventEntity lastEvent = dashRepo.findLastEventByExtension(ext).orElse(null);
            Long duration = null;
            if (lastEvent != null && isActive) {
                duration = java.time.Duration.between(lastEvent.getCreatedAt(), OffsetDateTime.now()).getSeconds();
            }

            return AgentStatusResponse.AgentCurrentStatus.builder()
                    .extension(ext).agentName(name).groupName(groupName).isActive(isActive)
                    .currentCallStatus(lastEvent != null ? lastEvent.getCallStatus() : null)
                    .currentCallFlow(lastEvent != null ? lastEvent.getCallFlow() : null)
                    .currentCallId(lastEvent != null && isActive ? lastEvent.getCallId() : null)
                    .callStartedAt(lastEvent != null && isActive ? lastEvent.getCreatedAt() : null)
                    .callDurationSeconds(duration)
                    .build();
        }).toList();

        long activeCount = statuses.stream().filter(AgentStatusResponse.AgentCurrentStatus::getIsActive).count();

        return AgentStatusResponse.builder()
                .agents(statuses).totalActive((int) activeCount)
                .totalIdle((int)(extensions.size() - activeCount))
                .asOf(OffsetDateTime.now()).build();
    }

    // ── Dashboard de ventas ──────────────────────────────────────────────────

    @Override
    public SalesDashboardResponse getSalesDashboard(Long ownerId, OffsetDateTime from, OffsetDateTime to) {
        var ownerUser = userRepo.findById(ownerId).orElse(null);
        var leads = leadRepo.findByOwnerId(ownerId);

        LocalDate fromDate = from.toLocalDate();
        LocalDate toDate = to.toLocalDate();
        var leadsInPeriod = leads.stream()
                .filter(l -> !l.getLeadDate().isBefore(fromDate) && !l.getLeadDate().isAfter(toDate))
                .toList();

        long total = leadsInPeriod.size();
        Map<LeadStatus, Long> byStatus = leadsInPeriod.stream()
                .collect(Collectors.groupingBy(l -> l.getStatus(), Collectors.counting()));

        long converted = byStatus.getOrDefault(LeadStatus.CONVERTED, 0L);
        long contacted = byStatus.getOrDefault(LeadStatus.CONTACTED, 0L)
                + byStatus.getOrDefault(LeadStatus.INTERESTED, 0L)
                + converted;
        double convRate = total > 0 ? Math.round((converted * 100.0 / total) * 10.0) / 10.0 : 0;
        double contactRate = total > 0 ? Math.round((contacted * 100.0 / total) * 10.0) / 10.0 : 0;

        // Por origen
        Map<String, List<com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity>> bySource =
                leadsInPeriod.stream().collect(Collectors.groupingBy(l -> l.getLeadSource() != null ? l.getLeadSource() : "Sin origen"));
        List<SalesDashboardResponse.SourceSummary> bySourceList = bySource.entrySet().stream().map(e -> {
            long src = e.getValue().size();
            long srcConv = e.getValue().stream().filter(l -> l.getStatus() == LeadStatus.CONVERTED).count();
            double srcRate = src > 0 ? Math.round((srcConv * 100.0 / src) * 10.0) / 10.0 : 0;
            return SalesDashboardResponse.SourceSummary.builder()
                    .source(e.getKey()).total(src).converted(srcConv).conversionRate(srcRate).build();
        }).sorted(Comparator.comparingLong(SalesDashboardResponse.SourceSummary::getTotal).reversed()).toList();

        // Callbacks
        long pendingCb = leadsInPeriod.stream().filter(l -> l.getStatus() == LeadStatus.CALLBACK && l.getCallbackDate() != null && !l.getCallbackDate().isBefore(LocalDate.now())).count();
        long overdueCb = leadsInPeriod.stream().filter(l -> l.getStatus() == LeadStatus.CALLBACK && l.getCallbackDate() != null && l.getCallbackDate().isBefore(LocalDate.now())).count();

        // Por agente asignado
        Map<Long, List<com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity>> byAgent =
                leadsInPeriod.stream().filter(l -> l.getAssignedAgentId() != null)
                        .collect(Collectors.groupingBy(l -> l.getAssignedAgentId()));
        List<SalesDashboardResponse.AssignedAgentSummary> agentSummaries = byAgent.entrySet().stream().map(e -> {
            var agent = agentRepo.findById(e.getKey()).orElse(null);
            String name = agent != null ? userRepo.findById(agent.getUserId()).map(u -> u.getName()).orElse("?") : "?";
            long ag = e.getValue().size();
            long agContacted = e.getValue().stream().filter(l -> l.getStatus() != LeadStatus.NEW && l.getStatus() != LeadStatus.PENDING).count();
            long agConverted = e.getValue().stream().filter(l -> l.getStatus() == LeadStatus.CONVERTED).count();
            double agRate = ag > 0 ? Math.round((agConverted * 100.0 / ag) * 10.0) / 10.0 : 0;
            return SalesDashboardResponse.AssignedAgentSummary.builder()
                    .agentId(e.getKey()).agentName(name)
                    .assignedLeads(ag).contactedLeads(agContacted).convertedLeads(agConverted).conversionRate(agRate)
                    .build();
        }).sorted(Comparator.comparingLong(SalesDashboardResponse.AssignedAgentSummary::getConvertedLeads).reversed()).toList();

        return SalesDashboardResponse.builder()
                .ownerName(ownerUser != null ? ownerUser.getName() : "").ownerId(ownerId)
                .totalLeads(total)
                .newLeads(byStatus.getOrDefault(LeadStatus.NEW, 0L))
                .pendingLeads(byStatus.getOrDefault(LeadStatus.PENDING, 0L))
                .contactedLeads(byStatus.getOrDefault(LeadStatus.CONTACTED, 0L))
                .interestedLeads(byStatus.getOrDefault(LeadStatus.INTERESTED, 0L))
                .convertedLeads(converted).discardedLeads(byStatus.getOrDefault(LeadStatus.DISCARDED, 0L))
                .callbackLeads(byStatus.getOrDefault(LeadStatus.CALLBACK, 0L))
                .conversionRate(convRate).contactRate(contactRate)
                .leadsBySource(bySourceList)
                .pendingCallbacks(pendingCb).overdueCallbacks(overdueCb)
                .assignedAgents(agentSummaries)
                .build();
    }

    private RecentCallResponse toRecentCall(CallEventEntity e) {
        return RecentCallResponse.builder()
                .callId(e.getCallId()).callerIdNum(e.getCallerIdNum())
                .callerIdName(e.getCallerIdName()).calledNumber(e.getCalledNumber())
                .finalStatus(e.getCallStatus()).callFlow(e.getCallFlow())
                .startedAt(e.getCreatedAt()).build();
    }
}
