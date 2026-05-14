package com.monitor.call.domain.usecases;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.in.DashboardUseCases;
import com.monitor.call.domain.ports.in.ScheduleUseCases;
import com.monitor.call.domain.ports.out.*;
import com.monitor.call.domain.responses.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardImpl implements DashboardUseCases {

    private static final String[] DOW_NAMES = {"Dom","Lun","Mar","Mie","Jue","Vie","Sab"};
    private static final long LONG_CALL_THRESHOLD_SECONDS = 1200;
    private static final long INACTIVE_THRESHOLD_MINUTES = 30;
    private static final int GRACE_MINUTES = 15;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final DashboardRepositoryPort dashRepo;
    private final AgentRepositoryPort agentPort;
    private final AgentGroupRepositoryPort groupPort;
    private final UserRepositoryPort userPort;
    private final CallTypificationRepositoryPort typPort;
    private final LeadRepositoryPort leadPort;
    private final ScheduleUseCases scheduleService;

    public DashboardImpl(DashboardRepositoryPort dashRepo,
                         AgentRepositoryPort agentPort,
                         AgentGroupRepositoryPort groupPort,
                         UserRepositoryPort userPort,
                         CallTypificationRepositoryPort typPort,
                         LeadRepositoryPort leadPort,
                         ScheduleUseCases scheduleService) {
        this.dashRepo        = dashRepo;
        this.agentPort       = agentPort;
        this.groupPort       = groupPort;
        this.userPort        = userPort;
        this.typPort         = typPort;
        this.leadPort        = leadPort;
        this.scheduleService = scheduleService;
    }

    // ── Dashboard del agente ─────────────────────────────────────────────────

    @Override
    public AgentDashboardResponse getAgentDashboard(String extension, OffsetDateTime from, OffsetDateTime to) {
        var agentOpt = agentPort.findByExtension(extension).orElseThrow(() -> new RuntimeException("Agente no encontrado: " + extension));
        String agentName = userPort.findById(agentOpt.getUserId()).map(u -> u.getName()).orElse(extension);

        long total    = dashRepo.countTotalCalls(extension, from, to);
        long answered = dashRepo.countAnsweredCalls(extension, from, to);
        long missed   = dashRepo.countMissedCalls(extension, from, to);
        long outbound = dashRepo.countOutboundCalls(extension, from, to);
        long inbound  = dashRepo.countInboundCalls(extension, from, to);
        double answerRate = total > 0 ? Math.round((answered * 100.0 / total) * 10.0) / 10.0 : 0;

        Double totalDur  = dashRepo.sumDurationSeconds(extension, from, to);
        long totalDurLong = totalDur != null ? totalDur.longValue() : 0;
        double avgDur    = answered > 0 ? Math.round((totalDurLong * 1.0 / answered) * 10.0) / 10.0 : 0;
        Double maxDur    = dashRepo.maxDurationSeconds(extension, from, to);
        Double minDur    = dashRepo.minDurationSeconds(extension, from, to);
        long shortCalls  = dashRepo.countShortCalls(extension, from, to);
        long longCalls   = dashRepo.countLongCalls(extension, from, to);

        double hours = java.time.Duration.between(from, to).toHours();
        double callsPerHour = hours > 0 ? Math.round((total / hours) * 10.0) / 10.0 : 0;

        long typified = typPort.findByAgentId(agentOpt.getId()).stream()
                .filter(t -> t.getCreatedAt().isAfter(from) && t.getCreatedAt().isBefore(to))
                .count();
        long untypified = answered - typified;

        List<Object[]> resultRows = typPort.countByResultForAgent(agentOpt.getId(), from, to);
        List<AgentDashboardResponse.ResultCount> resultDist = resultRows.stream()
                .map(r -> AgentDashboardResponse.ResultCount.builder()
                        .result(r[0].toString()).count(((Number) r[1]).longValue()).build())
                .toList();

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

        List<RecentCallResponse> recent = dashRepo.findRecentEvents(List.of(extension), 20).stream()
                .map(this::toRecentCall).toList();

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
                ? groupPort.findById(groupId).map(List::of).orElse(List.of())
                : groupPort.findByAdminId(adminId).stream().filter(g -> Boolean.TRUE.equals(g.getActive())).toList();

        var allExtensions = groups.stream()
                .flatMap(g -> agentPort.findExtensionsByGroupId(g.getId()).stream())
                .toList();

        if (allExtensions.isEmpty()) return AdminDashboardResponse.builder().adminEmail("").build();

        List<Object[]> summary = dashRepo.getCallSummaryByExtensions(allExtensions, from, to);
        long totalCalls = 0, answeredCalls = 0, missedCalls = 0;
        for (Object[] row : summary) {
            totalCalls   += ((Number) row[1]).longValue();
            answeredCalls += ((Number) row[2]).longValue();
            missedCalls  += ((Number) row[3]).longValue();
        }
        Double totalDur    = dashRepo.sumDurationByExtensions(allExtensions, from, to);
        long totalDurLong  = totalDur != null ? totalDur.longValue() : 0;
        double answerRate  = totalCalls > 0 ? Math.round((answeredCalls * 100.0 / totalCalls) * 10.0) / 10.0 : 0;
        double avgDur      = answeredCalls > 0 ? Math.round((totalDurLong * 1.0 / answeredCalls) * 10.0) / 10.0 : 0;

        Map<String, Object[]> summaryMap = summary.stream()
                .collect(Collectors.toMap(r -> (String) r[0], r -> r));

        List<AdminDashboardResponse.GroupSummary> groupSummaries = groups.stream().map(g -> {
            var exts = agentPort.findExtensionsByGroupId(g.getId());
            long gTotal = 0, gAnswered = 0;
            for (String e : exts) {
                if (summaryMap.containsKey(e)) {
                    gTotal   += ((Number) summaryMap.get(e)[1]).longValue();
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

        List<String> activeExts = dashRepo.findActiveExtensions(allExtensions);
        List<AdminDashboardResponse.AgentSummary> ranking = allExtensions.stream().map(ext -> {
            var agent   = agentPort.findByExtension(ext).orElse(null);
            String name = agent != null ? userPort.findById(agent.getUserId()).map(u -> u.getName()).orElse(ext) : ext;
            String groupName = agent != null && agent.getGroupName() != null ? agent.getGroupName() : "";
            Object[] row = summaryMap.get(ext);
            long extTotal    = row != null ? ((Number) row[1]).longValue() : 0;
            long extAnswered = row != null ? ((Number) row[2]).longValue() : 0;
            Double extDur    = dashRepo.sumDurationSeconds(ext, from, to);
            long extDurLong  = extDur != null ? extDur.longValue() : 0;
            double extRate   = extTotal > 0 ? Math.round((extAnswered * 100.0 / extTotal) * 10.0) / 10.0 : 0;
            double extAvg    = extAnswered > 0 ? Math.round((extDurLong * 1.0 / extAnswered) * 10.0) / 10.0 : 0;

            long agentId = agent != null ? agent.getId() : -1;
            long sales = agentId > 0 ? typPort.countByResultForAgent(agentId, from, to).stream()
                    .filter(r -> "SALE".equals(r[0].toString())).mapToLong(r -> ((Number) r[1]).longValue()).sum() : 0;
            double convRate = extAnswered > 0 ? Math.round((sales * 100.0 / extAnswered) * 10.0) / 10.0 : 0;

            return AdminDashboardResponse.AgentSummary.builder()
                    .extension(ext).agentName(name).groupName(groupName)
                    .isActive(activeExts.contains(ext))
                    .totalCalls(extTotal).answeredCalls(extAnswered).answerRate(extRate)
                    .totalDurationSeconds(extDurLong).avgDurationSeconds(extAvg).conversionRate(convRate)
                    .build();
        }).sorted(Comparator.comparingLong(AdminDashboardResponse.AgentSummary::getTotalCalls).reversed()).toList();

        List<AdminDashboardResponse.DailyTrend> trend = dashRepo.countByDayAndExtension(allExtensions, from, to).stream()
                .map(r -> {
                    String ext  = (String) r[1];
                    var agent   = agentPort.findByExtension(ext).orElse(null);
                    String name = agent != null ? userPort.findById(agent.getUserId()).map(u -> u.getName()).orElse(ext) : ext;
                    return AdminDashboardResponse.DailyTrend.builder()
                            .date(r[0].toString()).extension(ext).agentName(name)
                            .count(((Number) r[2]).longValue()).build();
                }).toList();

        List<String> alerts = new ArrayList<>();
        List<String> longCallsList = dashRepo.findLongActiveCalls(allExtensions, LONG_CALL_THRESHOLD_SECONDS);
        if (!longCallsList.isEmpty()) alerts.add("Llamadas largas activas (>20min): extensiones " + String.join(", ", longCallsList));
        List<String> inactive = dashRepo.findInactiveExtensions(allExtensions, OffsetDateTime.now().minusMinutes(INACTIVE_THRESHOLD_MINUTES));
        if (!inactive.isEmpty()) alerts.add("Agentes inactivos (>30min): extensiones " + String.join(", ", inactive));

        var adminUser = userPort.findById(adminId).orElse(null);

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
                ? agentPort.findExtensionsByGroupId(groupId)
                : agentPort.findExtensionsByAdminId(adminId);

        List<String> activeExts = dashRepo.findActiveExtensions(extensions);

        List<AgentStatusResponse.AgentCurrentStatus> statuses = extensions.stream().map(ext -> {
            var agent     = agentPort.findByExtension(ext).orElse(null);
            String name   = agent != null ? userPort.findById(agent.getUserId()).map(u -> u.getName()).orElse(ext) : ext;
            String grpName = agent != null && agent.getGroupName() != null ? agent.getGroupName() : "";
            boolean isActive = activeExts.contains(ext);

            CallEvent lastEvent = dashRepo.findLastEventByExtension(ext).orElse(null);
            Long duration = null;
            if (lastEvent != null && isActive) {
                duration = java.time.Duration.between(lastEvent.getCreatedAt(), OffsetDateTime.now()).getSeconds();
            }

            return AgentStatusResponse.AgentCurrentStatus.builder()
                    .extension(ext).agentName(name).groupName(grpName).isActive(isActive)
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
        var ownerUser = userPort.findById(ownerId).orElse(null);
        var leads = leadPort.findByOwnerId(ownerId);

        LocalDate fromDate = from.toLocalDate();
        LocalDate toDate   = to.toLocalDate();
        var leadsInPeriod  = leads.stream()
                .filter(l -> !l.getLeadDate().isBefore(fromDate) && !l.getLeadDate().isAfter(toDate))
                .toList();

        long total = leadsInPeriod.size();
        Map<com.monitor.call.domain.enums.LeadStatus, Long> byStatus = leadsInPeriod.stream()
                .collect(Collectors.groupingBy(l -> l.getStatus(), Collectors.counting()));

        long converted   = byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.CONVERTED, 0L);
        long contacted   = byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.CONTACTED, 0L)
                + byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.INTERESTED, 0L)
                + converted;
        double convRate    = total > 0 ? Math.round((converted * 100.0 / total) * 10.0) / 10.0 : 0;
        double contactRate = total > 0 ? Math.round((contacted * 100.0 / total) * 10.0) / 10.0 : 0;

        Map<String, List<com.monitor.call.domain.models.Lead>> bySource =
                leadsInPeriod.stream().collect(Collectors.groupingBy(l -> l.getLeadSource() != null ? l.getLeadSource() : "Sin origen"));
        List<SalesDashboardResponse.SourceSummary> bySourceList = bySource.entrySet().stream().map(e -> {
            long src = e.getValue().size();
            long srcConv = e.getValue().stream().filter(l -> l.getStatus() == com.monitor.call.domain.enums.LeadStatus.CONVERTED).count();
            double srcRate = src > 0 ? Math.round((srcConv * 100.0 / src) * 10.0) / 10.0 : 0;
            return SalesDashboardResponse.SourceSummary.builder()
                    .source(e.getKey()).total(src).converted(srcConv).conversionRate(srcRate).build();
        }).sorted(Comparator.comparingLong(SalesDashboardResponse.SourceSummary::getTotal).reversed()).toList();

        long pendingCb = leadsInPeriod.stream().filter(l -> l.getStatus() == com.monitor.call.domain.enums.LeadStatus.CALLBACK && l.getCallbackDate() != null && !l.getCallbackDate().isBefore(LocalDate.now())).count();
        long overdueCb = leadsInPeriod.stream().filter(l -> l.getStatus() == com.monitor.call.domain.enums.LeadStatus.CALLBACK && l.getCallbackDate() != null && l.getCallbackDate().isBefore(LocalDate.now())).count();

        Map<Long, List<com.monitor.call.domain.models.Lead>> byAgent =
                leadsInPeriod.stream().filter(l -> l.getAssignedAgentId() != null)
                        .collect(Collectors.groupingBy(l -> l.getAssignedAgentId()));
        List<SalesDashboardResponse.AssignedAgentSummary> agentSummaries = byAgent.entrySet().stream().map(e -> {
            var agent = agentPort.findById(e.getKey()).orElse(null);
            String name = agent != null ? userPort.findById(agent.getUserId()).map(u -> u.getName()).orElse("?") : "?";
            long ag = e.getValue().size();
            long agContacted = e.getValue().stream().filter(l -> l.getStatus() != com.monitor.call.domain.enums.LeadStatus.NEW && l.getStatus() != com.monitor.call.domain.enums.LeadStatus.PENDING).count();
            long agConverted = e.getValue().stream().filter(l -> l.getStatus() == com.monitor.call.domain.enums.LeadStatus.CONVERTED).count();
            double agRate = ag > 0 ? Math.round((agConverted * 100.0 / ag) * 10.0) / 10.0 : 0;
            return SalesDashboardResponse.AssignedAgentSummary.builder()
                    .agentId(e.getKey()).agentName(name)
                    .assignedLeads(ag).contactedLeads(agContacted).convertedLeads(agConverted).conversionRate(agRate)
                    .build();
        }).sorted(Comparator.comparingLong(SalesDashboardResponse.AssignedAgentSummary::getConvertedLeads).reversed()).toList();

        return SalesDashboardResponse.builder()
                .ownerName(ownerUser != null ? ownerUser.getName() : "").ownerId(ownerId)
                .totalLeads(total)
                .newLeads(byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.NEW, 0L))
                .pendingLeads(byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.PENDING, 0L))
                .contactedLeads(byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.CONTACTED, 0L))
                .interestedLeads(byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.INTERESTED, 0L))
                .convertedLeads(converted).discardedLeads(byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.DISCARDED, 0L))
                .callbackLeads(byStatus.getOrDefault(com.monitor.call.domain.enums.LeadStatus.CALLBACK, 0L))
                .conversionRate(convRate).contactRate(contactRate)
                .leadsBySource(bySourceList)
                .pendingCallbacks(pendingCb).overdueCallbacks(overdueCb)
                .assignedAgents(agentSummaries)
                .build();
    }

    // ── Cumplimiento de horarios ─────────────────────────────────────────────

    @Override
    public List<ScheduleAdherenceRow> getScheduleAdherence(Long adminId, LocalDate from, LocalDate to, Long agentId) {
        List<com.monitor.call.domain.models.Agent> agents = agentId != null
                ? agentPort.findById(agentId).map(List::of).orElse(List.of())
                : agentPort.findByAdminId(adminId);

        if (agents.isEmpty()) return List.of();

        List<String> extensions = agents.stream()
                .map(com.monitor.call.domain.models.Agent::getExtension).toList();

        OffsetDateTime fromDt = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toDt   = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Object[]> rows = dashRepo.findDailyActivitySummary(extensions, fromDt, toDt);

        // Index activity by extension+day
        java.util.Map<String, Object[]> activityMap = new java.util.HashMap<>();
        for (Object[] row : rows) {
            String key = row[0] + "|" + row[1];
            activityMap.put(key, row);
        }

        List<ScheduleAdherenceRow> result = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            for (com.monitor.call.domain.models.Agent agent : agents) {
                String agentName = userPort.findById(agent.getUserId())
                        .map(u -> u.getName()).orElse(agent.getExtension());
                ScheduleWindow window = scheduleService.getWindowForDay(adminId, day);

                String key = agent.getExtension() + "|" + day;
                Object[] activity = activityMap.get(key);
                long callCount = activity != null ? ((Number) activity[4]).longValue() : 0;

                OffsetDateTime firstCallOdt = activity != null ? toOdtAdherence(activity[2]) : null;
                OffsetDateTime lastCallOdt  = activity != null ? toOdtAdherence(activity[3]) : null;

                String firstCallStr = firstCallOdt != null ? firstCallOdt.atZoneSameInstant(ZoneOffset.UTC).format(TIME_FMT) : null;
                String lastCallStr  = lastCallOdt  != null ? lastCallOdt.atZoneSameInstant(ZoneOffset.UTC).format(TIME_FMT) : null;

                String status = computeStatus(window, callCount, firstCallOdt, lastCallOdt);

                result.add(ScheduleAdherenceRow.builder()
                        .date(day.toString())
                        .agentId(agent.getId())
                        .agentName(agentName)
                        .extension(agent.getExtension())
                        .scheduleType(window.type())
                        .expectedStart(window.windowStart() != null ? window.windowStart().format(TIME_FMT) : null)
                        .expectedEnd(window.windowEnd() != null ? window.windowEnd().format(TIME_FMT) : null)
                        .firstCallAt(firstCallStr)
                        .lastCallAt(lastCallStr)
                        .callCount(callCount)
                        .status(status)
                        .build());
            }
        }
        result.sort(java.util.Comparator.comparing(ScheduleAdherenceRow::getDate).reversed()
                .thenComparing(ScheduleAdherenceRow::getAgentName));
        return result;
    }

    private String computeStatus(ScheduleWindow window, long callCount,
                                  OffsetDateTime firstCall, OffsetDateTime lastCall) {
        return switch (window.type()) {
            case "FREE" -> "FREE";
            case "FIXED" -> {
                if (!window.isWorkDay()) yield "DAY_OFF";
                if (callCount == 0)     yield "ABSENT";
                LocalTime first = firstCall.atZoneSameInstant(ZoneOffset.UTC).toLocalTime();
                LocalTime last  = lastCall.atZoneSameInstant(ZoneOffset.UTC).toLocalTime();
                boolean lateStart   = first.isAfter(window.windowStart().plusMinutes(GRACE_MINUTES));
                boolean earlyLeave  = last.isBefore(window.windowEnd().minusMinutes(GRACE_MINUTES));
                if (lateStart)  yield "LATE";
                if (earlyLeave) yield "EARLY_LEAVE";
                yield "COMPLIANT";
            }
            case "HOURS_PER_DAY" -> {
                if (callCount == 0) yield "ABSENT";
                LocalTime first = firstCall.atZoneSameInstant(ZoneOffset.UTC).toLocalTime();
                LocalTime last  = lastCall.atZoneSameInstant(ZoneOffset.UTC).toLocalTime();
                boolean inWindow = !first.isAfter(window.windowEnd()) && !last.isBefore(window.windowStart());
                yield inWindow ? "COMPLIANT" : "OUT_OF_WINDOW";
            }
            default -> "FREE";
        };
    }

    private OffsetDateTime toOdtAdherence(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime odt) return odt;
        if (o instanceof java.time.Instant inst) return inst.atOffset(ZoneOffset.UTC);
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        return null;
    }

    private RecentCallResponse toRecentCall(CallEvent e) {
        return RecentCallResponse.builder()
                .callId(e.getCallId()).callerIdNum(e.getCallerIdNum())
                .callerIdName(e.getCallerIdName()).calledNumber(e.getCalledNumber())
                .finalStatus(e.getCallStatus()).callFlow(e.getCallFlow())
                .startedAt(e.getCreatedAt()).build();
    }
}
