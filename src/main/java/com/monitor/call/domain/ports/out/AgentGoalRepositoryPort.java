package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.enums.GoalPeriod;
import com.monitor.call.domain.models.AgentGoal;
import com.monitor.call.domain.models.AgentGoalHistory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AgentGoalRepositoryPort {

    AgentGoal save(AgentGoal goal);

    Optional<AgentGoal> findById(Long id);

    List<AgentGoal> findByAdminId(Long adminId);

    List<AgentGoal> findByAdminIdAndAgentId(Long adminId, Long agentId);

    List<AgentGoal> findActiveGoalsForAgent(Long adminId, Long agentId);

    List<AgentGoal> findActiveDailyGoals();

    List<AgentGoal> findByPeriod(GoalPeriod period);

    // History
    AgentGoalHistory saveHistory(AgentGoalHistory history);

    List<AgentGoalHistory> findHistoryByAgent(Long agentId);

    List<AgentGoalHistory> findHistoryByAgentAndDateRange(Long agentId, LocalDate from, LocalDate to);

    List<AgentGoalHistory> findHistoryByGoalIds(List<Long> goalIds);

    Optional<AgentGoalHistory> findHistoryByGoalAndAgentAndDate(Long goalId, Long agentId, LocalDate date);
}
