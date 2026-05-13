package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.AgentGoal;
import com.monitor.call.domain.models.AgentGoalHistory;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.ports.in.AgentGoalUseCases;
import com.monitor.call.domain.ports.out.AgentGoalRepositoryPort;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.CallTypificationRepositoryPort;
import com.monitor.call.domain.ports.out.DashboardRepositoryPort;
import com.monitor.call.domain.responses.AgentGoalHistoryResponse;
import com.monitor.call.domain.responses.AgentGoalResponse;
import com.monitor.call.domain.responses.GoalProgressResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AgentGoalImpl implements AgentGoalUseCases {

    private static final Logger logger = LoggerFactory.getLogger(AgentGoalImpl.class);

    private final AgentGoalRepositoryPort goalRepo;
    private final AgentRepositoryPort agentRepo;
    private final DashboardRepositoryPort dashboardRepo;
    private final CallTypificationRepositoryPort typificationRepo;

    public AgentGoalImpl(AgentGoalRepositoryPort goalRepo,
                         AgentRepositoryPort agentRepo,
                         DashboardRepositoryPort dashboardRepo,
                         CallTypificationRepositoryPort typificationRepo) {
        this.goalRepo = goalRepo;
        this.agentRepo = agentRepo;
        this.dashboardRepo = dashboardRepo;
        this.typificationRepo = typificationRepo;
    }

    // ── Admin: CRUD ───────────────────────────────────────────────────────────

    @Override
    public AgentGoalResponse createGoal(Long adminId, Long agentId,
                                        GoalKpi kpiType, GoalPeriod period, Double targetValue) {
        AgentGoal goal = AgentGoal.builder()
                .adminId(adminId)
                .agentId(agentId)
                .kpiType(kpiType)
                .period(period)
                .targetValue(targetValue)
                .active(true)
                .build();
        AgentGoal saved = goalRepo.save(goal);
        logger.info("Meta creada: adminId={} agentId={} kpi={} period={} target={}",
                adminId, agentId, kpiType, period, targetValue);
        return toResponse(saved);
    }

    @Override
    public AgentGoalResponse updateGoal(Long goalId, Double targetValue, Long adminId) {
        AgentGoal goal = goalRepo.findById(goalId)
                .filter(g -> g.getAdminId().equals(adminId))
                .orElseThrow(() -> new RuntimeException("Meta no encontrada: " + goalId));
        goal.setTargetValue(targetValue);
        AgentGoal saved = goalRepo.save(goal);
        logger.info("Meta actualizada: goalId={} targetValue={}", goalId, targetValue);
        return toResponse(saved);
    }

    @Override
    public void deactivateGoal(Long goalId, Long adminId) {
        AgentGoal goal = goalRepo.findById(goalId)
                .filter(g -> g.getAdminId().equals(adminId))
                .orElseThrow(() -> new RuntimeException("Meta no encontrada: " + goalId));
        goal.setActive(false);
        goalRepo.save(goal);
        logger.info("Meta desactivada: goalId={}", goalId);
    }

    @Override
    public List<AgentGoalResponse> listGoals(Long adminId, Long agentId) {
        List<AgentGoal> goals = agentId != null
                ? goalRepo.findByAdminIdAndAgentId(adminId, agentId)
                : goalRepo.findByAdminId(adminId);
        return goals.stream().map(this::toResponse).toList();
    }

    @Override
    public List<AgentGoalHistoryResponse> getHistory(Long adminId, Long agentId,
                                                     LocalDate from, LocalDate to) {
        // Resolve agent IDs in scope of this admin
        List<Long> agentIds;
        if (agentId != null) {
            agentIds = List.of(agentId);
        } else {
            agentIds = agentRepo.findByAdminId(adminId).stream()
                    .map(Agent::getId).toList();
        }

        // Get all active goals for this admin to build a goalId→goal map
        List<AgentGoal> adminGoals = goalRepo.findByAdminId(adminId);
        Map<Long, AgentGoal> goalMap = adminGoals.stream()
                .collect(Collectors.toMap(AgentGoal::getId, g -> g));

        // Build agent name cache
        Map<Long, String> agentNames = buildAgentNameCache(agentIds);

        List<AgentGoalHistory> histories = new ArrayList<>();
        for (Long aid : agentIds) {
            if (from != null && to != null) {
                histories.addAll(goalRepo.findHistoryByAgentAndDateRange(aid, from, to));
            } else {
                histories.addAll(goalRepo.findHistoryByAgent(aid));
            }
        }

        return histories.stream()
                .map(h -> toHistoryResponse(h, goalMap, agentNames))
                .toList();
    }

    // ── Agent: progress ───────────────────────────────────────────────────────

    @Override
    public List<GoalProgressResponse> getMyGoals(Long agentId, Long adminId) {
        Agent agent = agentRepo.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente no encontrado: " + agentId));

        List<AgentGoal> goals = goalRepo.findActiveGoalsForAgent(adminId, agentId);
        List<GoalProgressResponse> result = new ArrayList<>();

        for (AgentGoal goal : goals) {
            double actual = computeActualValue(goal, agent);
            double target = goal.getTargetValue();
            double progress = target > 0 ? Math.min(actual / target * 100.0, 999.9) : 0.0;

            result.add(GoalProgressResponse.builder()
                    .goalId(goal.getId())
                    .kpiType(goal.getKpiType())
                    .period(goal.getPeriod())
                    .targetValue(target)
                    .actualValue(actual)
                    .progressPercent(Math.round(progress * 10.0) / 10.0)
                    .achieved(actual >= target)
                    .build());
        }
        return result;
    }

    // ── End-of-day evaluation ─────────────────────────────────────────────────

    @Override
    public List<AgentGoalHistoryResponse> evaluateDailyGoals() {
        List<AgentGoal> dailyGoals = goalRepo.findActiveDailyGoals();
        LocalDate today = LocalDate.now();
        List<AgentGoalHistoryResponse> unmet = new ArrayList<>();

        // Cache to avoid repeated DB lookups per agent
        Map<Long, Agent> agentCache = new ConcurrentHashMap<>();

        for (AgentGoal goal : dailyGoals) {
            // If goal is for a specific agent, evaluate only that agent
            // If agentId is null, goal applies to ALL agents in that admin
            List<Long> targets;
            if (goal.getAgentId() != null) {
                targets = List.of(goal.getAgentId());
            } else {
                targets = agentRepo.findByAdminId(goal.getAdminId())
                        .stream().map(Agent::getId).toList();
            }

            for (Long agentId : targets) {
                // Skip if history already recorded today
                if (goalRepo.findHistoryByGoalAndAgentAndDate(goal.getId(), agentId, today).isPresent()) {
                    continue;
                }

                Agent agent = agentCache.computeIfAbsent(agentId,
                        id -> agentRepo.findById(id).orElse(null));
                if (agent == null) continue;

                double actual = computeActualValue(goal, agent);
                boolean achieved = actual >= goal.getTargetValue();

                AgentGoalHistory history = AgentGoalHistory.builder()
                        .goalId(goal.getId())
                        .agentId(agentId)
                        .snapshotDate(today)
                        .targetValue(goal.getTargetValue())
                        .actualValue(actual)
                        .achieved(achieved)
                        .build();
                AgentGoalHistory saved = goalRepo.saveHistory(history);
                logger.info("Historial de meta guardado: goalId={} agentId={} achieved={}", goal.getId(), agentId, achieved);

                if (!achieved) {
                    AgentGoalHistoryResponse resp = toHistoryResponse(saved,
                            Map.of(goal.getId(), goal),
                            Map.of(agentId, agent.getUserName() != null ? agent.getUserName() : "Agente " + agentId));
                    unmet.add(resp);
                }
            }
        }
        return unmet;
    }

    // ── KPI computation ───────────────────────────────────────────────────────

    private double computeActualValue(AgentGoal goal, Agent agent) {
        OffsetDateTime[] range = dateRange(goal.getPeriod());
        OffsetDateTime from = range[0];
        OffsetDateTime to   = range[1];
        String ext = agent.getExtension();
        Long agentId = agent.getId();

        return switch (goal.getKpiType()) {
            case TOTAL_CALLS -> (double) dashboardRepo.countTotalCalls(ext, from, to);
            case ANSWERED_CALLS -> (double) dashboardRepo.countAnsweredCalls(ext, from, to);
            case ANSWER_RATE -> {
                long total = dashboardRepo.countTotalCalls(ext, from, to);
                if (total == 0) yield 0.0;
                long answered = dashboardRepo.countAnsweredCalls(ext, from, to);
                yield Math.round(answered * 1000.0 / total) / 10.0; // one decimal %
            }
            case TYPIFIED_CALLS -> {
                List<CallTypification> typs = typificationRepo.findByAgentAndPeriod(agentId, from, to);
                yield (double) typs.size();
            }
            case CONVERSIONS -> {
                List<CallTypification> typs = typificationRepo.findByAgentAndPeriod(agentId, from, to);
                yield typs.stream()
                        .filter(t -> "SALE".equalsIgnoreCase(String.valueOf(t.getResult())))
                        .count();
            }
            case CONVERSION_RATE -> {
                List<CallTypification> typs = typificationRepo.findByAgentAndPeriod(agentId, from, to);
                if (typs.isEmpty()) yield 0.0;
                long sales = typs.stream()
                        .filter(t -> "SALE".equalsIgnoreCase(String.valueOf(t.getResult())))
                        .count();
                yield Math.round(sales * 1000.0 / typs.size()) / 10.0;
            }
            case APPOINTMENTS -> {
                List<CallTypification> typs = typificationRepo.findByAgentAndPeriod(agentId, from, to);
                yield typs.stream()
                        .filter(t -> "APPOINTMENT".equalsIgnoreCase(String.valueOf(t.getResult())))
                        .count();
            }
            case CALLBACKS_HANDLED -> {
                List<CallTypification> typs = typificationRepo.findByAgentAndPeriod(agentId, from, to);
                yield typs.stream()
                        .filter(t -> "CALLBACK".equalsIgnoreCase(String.valueOf(t.getResult())))
                        .count();
            }
            case AVG_CALL_DURATION -> {
                long total = dashboardRepo.countTotalCalls(ext, from, to);
                if (total == 0) yield 0.0;
                Double sum = dashboardRepo.sumDurationSeconds(ext, from, to);
                yield sum != null ? Math.round(sum / total * 10.0) / 10.0 : 0.0;
            }
        };
    }

    // ── Date range helpers ────────────────────────────────────────────────────

    private OffsetDateTime[] dateRange(GoalPeriod period) {
        LocalDate today = LocalDate.now();
        ZoneOffset offset = ZoneOffset.UTC;
        return switch (period) {
            case DAILY -> new OffsetDateTime[]{
                    today.atStartOfDay().atOffset(offset),
                    today.atTime(23, 59, 59).atOffset(offset)
            };
            case WEEKLY -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = monday.plusDays(6);
                yield new OffsetDateTime[]{
                        monday.atStartOfDay().atOffset(offset),
                        sunday.atTime(23, 59, 59).atOffset(offset)
                };
            }
            case MONTHLY -> {
                LocalDate first = today.with(TemporalAdjusters.firstDayOfMonth());
                LocalDate last  = today.with(TemporalAdjusters.lastDayOfMonth());
                yield new OffsetDateTime[]{
                        first.atStartOfDay().atOffset(offset),
                        last.atTime(23, 59, 59).atOffset(offset)
                };
            }
        };
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private AgentGoalResponse toResponse(AgentGoal g) {
        String agentName = null;
        if (g.getAgentId() != null) {
            agentName = agentRepo.findById(g.getAgentId())
                    .map(Agent::getUserName).orElse(null);
        }
        return AgentGoalResponse.builder()
                .id(g.getId())
                .adminId(g.getAdminId())
                .agentId(g.getAgentId())
                .agentName(agentName)
                .kpiType(g.getKpiType())
                .period(g.getPeriod())
                .targetValue(g.getTargetValue())
                .active(g.getActive())
                .createdAt(g.getCreatedAt())
                .build();
    }

    private AgentGoalHistoryResponse toHistoryResponse(AgentGoalHistory h,
                                                        Map<Long, AgentGoal> goalMap,
                                                        Map<Long, String> agentNames) {
        AgentGoal goal = goalMap.get(h.getGoalId());
        double target = h.getTargetValue();
        double actual = h.getActualValue();
        double progress = target > 0 ? Math.round(actual / target * 1000.0) / 10.0 : 0.0;
        return AgentGoalHistoryResponse.builder()
                .id(h.getId())
                .goalId(h.getGoalId())
                .kpiType(goal != null ? goal.getKpiType() : null)
                .period(goal != null ? goal.getPeriod() : null)
                .agentId(h.getAgentId())
                .agentName(agentNames.getOrDefault(h.getAgentId(), "Agente " + h.getAgentId()))
                .snapshotDate(h.getSnapshotDate())
                .targetValue(target)
                .actualValue(actual)
                .progressPercent(progress)
                .achieved(h.getAchieved())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private Map<Long, String> buildAgentNameCache(List<Long> agentIds) {
        Map<Long, String> cache = new ConcurrentHashMap<>();
        for (Long id : agentIds) {
            agentRepo.findById(id).ifPresent(a ->
                    cache.put(id, a.getUserName() != null ? a.getUserName() : "Agente " + id));
        }
        return cache;
    }
}
