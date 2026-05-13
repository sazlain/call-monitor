package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import com.monitor.call.domain.models.AgentGoal;
import com.monitor.call.domain.models.AgentGoalHistory;
import com.monitor.call.domain.ports.out.AgentGoalRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGoalEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGoalHistoryEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentGoalHistoryJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentGoalJpaRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class AgentGoalRepositoryImpl implements AgentGoalRepositoryPort {

    private final AgentGoalJpaRepository goalRepo;
    private final AgentGoalHistoryJpaRepository historyRepo;

    public AgentGoalRepositoryImpl(AgentGoalJpaRepository goalRepo,
                                   AgentGoalHistoryJpaRepository historyRepo) {
        this.goalRepo = goalRepo;
        this.historyRepo = historyRepo;
    }

    // ── Goals ──────────────────────────────────────────────────────────────────

    @Override public AgentGoal save(AgentGoal goal) {
        return toModel(goalRepo.save(toEntity(goal)));
    }

    @Override public Optional<AgentGoal> findById(Long id) {
        return goalRepo.findById(id).map(this::toModel);
    }

    @Override public List<AgentGoal> findByAdminId(Long adminId) {
        return goalRepo.findByAdminIdAndActiveTrue(adminId).stream().map(this::toModel).toList();
    }

    @Override public List<AgentGoal> findByAdminIdAndAgentId(Long adminId, Long agentId) {
        return goalRepo.findByAdminIdAndAgentIdAndActiveTrue(adminId, agentId)
                .stream().map(this::toModel).toList();
    }

    @Override public List<AgentGoal> findActiveGoalsForAgent(Long adminId, Long agentId) {
        return goalRepo.findActiveGoalsForAgent(adminId, agentId).stream().map(this::toModel).toList();
    }

    @Override public List<AgentGoal> findActiveDailyGoals() {
        return goalRepo.findByActiveTrueAndPeriod(GoalPeriod.DAILY).stream().map(this::toModel).toList();
    }

    @Override public List<AgentGoal> findByPeriod(GoalPeriod period) {
        return goalRepo.findByActiveTrueAndPeriod(period).stream().map(this::toModel).toList();
    }

    // ── History ────────────────────────────────────────────────────────────────

    @Override public AgentGoalHistory saveHistory(AgentGoalHistory h) {
        return toHistoryModel(historyRepo.save(toHistoryEntity(h)));
    }

    @Override public List<AgentGoalHistory> findHistoryByAgent(Long agentId) {
        return historyRepo.findByAgentIdOrderBySnapshotDateDesc(agentId)
                .stream().map(this::toHistoryModel).toList();
    }

    @Override public List<AgentGoalHistory> findHistoryByAgentAndDateRange(
            Long agentId, LocalDate from, LocalDate to) {
        return historyRepo.findByAgentIdAndSnapshotDateBetweenOrderBySnapshotDateDesc(agentId, from, to)
                .stream().map(this::toHistoryModel).toList();
    }

    @Override public List<AgentGoalHistory> findHistoryByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) return List.of();
        return historyRepo.findByGoalIdInOrderBySnapshotDateDesc(goalIds)
                .stream().map(this::toHistoryModel).toList();
    }

    @Override public Optional<AgentGoalHistory> findHistoryByGoalAndAgentAndDate(
            Long goalId, Long agentId, LocalDate date) {
        return historyRepo.findByGoalIdAndAgentIdAndSnapshotDate(goalId, agentId, date)
                .map(this::toHistoryModel);
    }

    // ── Mappers ────────────────────────────────────────────────────────────────

    private AgentGoal toModel(AgentGoalEntity e) {
        return AgentGoal.builder()
                .id(e.getId()).adminId(e.getAdminId()).agentId(e.getAgentId())
                .kpiType(e.getKpiType()).period(e.getPeriod())
                .targetValue(e.getTargetValue()).active(e.getActive())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    private AgentGoalEntity toEntity(AgentGoal m) {
        return AgentGoalEntity.builder()
                .id(m.getId()).adminId(m.getAdminId()).agentId(m.getAgentId())
                .kpiType(m.getKpiType()).period(m.getPeriod())
                .targetValue(m.getTargetValue())
                .active(m.getActive() != null ? m.getActive() : true)
                .build();
    }

    private AgentGoalHistory toHistoryModel(AgentGoalHistoryEntity e) {
        return AgentGoalHistory.builder()
                .id(e.getId()).goalId(e.getGoalId()).agentId(e.getAgentId())
                .snapshotDate(e.getSnapshotDate()).targetValue(e.getTargetValue())
                .actualValue(e.getActualValue()).achieved(e.getAchieved())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private AgentGoalHistoryEntity toHistoryEntity(AgentGoalHistory m) {
        return AgentGoalHistoryEntity.builder()
                .id(m.getId()).goalId(m.getGoalId()).agentId(m.getAgentId())
                .snapshotDate(m.getSnapshotDate()).targetValue(m.getTargetValue())
                .actualValue(m.getActualValue()).achieved(m.getAchieved())
                .build();
    }
}
