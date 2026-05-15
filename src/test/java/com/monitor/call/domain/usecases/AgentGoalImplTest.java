package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.AgentGoal;
import com.monitor.call.domain.models.AgentGoalHistory;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.ports.out.AgentGoalRepositoryPort;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.CallTypificationRepositoryPort;
import com.monitor.call.domain.ports.out.DashboardRepositoryPort;
import com.monitor.call.domain.responses.AgentGoalHistoryResponse;
import com.monitor.call.domain.responses.AgentGoalResponse;
import com.monitor.call.domain.responses.GoalProgressResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentGoalImplTest {

    @Mock private AgentGoalRepositoryPort goalRepo;
    @Mock private AgentRepositoryPort agentRepo;
    @Mock private DashboardRepositoryPort dashboardRepo;
    @Mock private CallTypificationRepositoryPort typificationRepo;

    @InjectMocks
    private AgentGoalImpl agentGoalImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private AgentGoal buildGoal(Long id, Long adminId, Long agentId, GoalKpi kpi, GoalPeriod period) {
        return AgentGoal.builder()
                .id(id).adminId(adminId).agentId(agentId)
                .kpiType(kpi).period(period).targetValue(10.0)
                .active(true).createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private Agent buildAgent(Long id, String ext) {
        return Agent.builder().id(id).userId(100L).extension(ext)
                .active(true).userName("Agent " + id).build();
    }

    private AgentGoalHistory buildHistory(Long id, Long goalId, Long agentId, double actual) {
        return AgentGoalHistory.builder()
                .id(id).goalId(goalId).agentId(agentId)
                .snapshotDate(LocalDate.now()).targetValue(10.0).actualValue(actual)
                .achieved(actual >= 10.0).createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private CallTypification buildTypification(String callId, Long agentId, String result) {
        return CallTypification.builder()
                .id(1L).callId(callId).agentId(agentId)
                .result(com.monitor.call.domain.enums.CallResult.valueOf(result))
                .build();
    }

    // ─── createGoal ──────────────────────────────────────────────────────────

    @Test
    void createGoal_savesAndReturnsResponse() {
        AgentGoal saved = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        when(goalRepo.save(any())).thenReturn(saved);

        AgentGoalResponse resp = agentGoalImpl.createGoal(1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY, 10.0);

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getKpiType()).isEqualTo(GoalKpi.TOTAL_CALLS);
        verify(goalRepo).save(any());
    }

    @Test
    void createGoal_withAgentId_fetchesAgentName() {
        AgentGoal saved = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        when(goalRepo.save(any())).thenReturn(saved);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(buildAgent(10L, "1001")));

        AgentGoalResponse resp = agentGoalImpl.createGoal(1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY, 10.0);

        assertThat(resp.getAgentName()).isEqualTo("Agent 10");
    }

    // ─── updateGoal ──────────────────────────────────────────────────────────

    @Test
    void updateGoal_found_updatesTargetAndSaves() {
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        when(goalRepo.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepo.save(any())).thenReturn(goal);

        AgentGoalResponse resp = agentGoalImpl.updateGoal(1L, 20.0, 1L);

        verify(goalRepo).save(argThat(g -> g.getTargetValue() == 20.0));
        assertThat(resp).isNotNull();
    }

    @Test
    void updateGoal_notFound_throwsRuntimeException() {
        when(goalRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGoalImpl.updateGoal(99L, 20.0, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Meta");
    }

    @Test
    void updateGoal_wrongAdmin_throwsRuntimeException() {
        AgentGoal goal = buildGoal(1L, 2L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        when(goalRepo.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> agentGoalImpl.updateGoal(1L, 20.0, 1L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── deactivateGoal ───────────────────────────────────────────────────────

    @Test
    void deactivateGoal_found_setsActiveFalse() {
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        when(goalRepo.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepo.save(any())).thenReturn(goal);

        agentGoalImpl.deactivateGoal(1L, 1L);

        verify(goalRepo).save(argThat(g -> !g.getActive()));
    }

    @Test
    void deactivateGoal_notFound_throwsRuntimeException() {
        when(goalRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGoalImpl.deactivateGoal(99L, 1L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── listGoals ────────────────────────────────────────────────────────────

    @Test
    void listGoals_withAgentId_queriesByAdminAndAgent() {
        AgentGoal g = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        when(goalRepo.findByAdminIdAndAgentId(1L, 10L)).thenReturn(List.of(g));

        List<AgentGoalResponse> result = agentGoalImpl.listGoals(1L, 10L);

        assertThat(result).hasSize(1);
        verify(goalRepo).findByAdminIdAndAgentId(1L, 10L);
    }

    @Test
    void listGoals_withoutAgentId_queriesByAdmin() {
        when(goalRepo.findByAdminId(1L)).thenReturn(List.of(
                buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY),
                buildGoal(2L, 1L, 11L, GoalKpi.CONVERSIONS, GoalPeriod.WEEKLY)));

        List<AgentGoalResponse> result = agentGoalImpl.listGoals(1L, null);

        assertThat(result).hasSize(2);
        verify(goalRepo).findByAdminId(1L);
    }

    // ─── getHistory ───────────────────────────────────────────────────────────

    @Test
    void getHistory_specificAgent_withDateRange_queriesHistoryByRange() {
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        AgentGoalHistory hist = buildHistory(1L, 1L, 10L, 8.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(buildAgent(10L, "1001")));
        when(goalRepo.findByAdminId(1L)).thenReturn(List.of(goal));
        when(goalRepo.findHistoryByAgentAndDateRange(eq(10L), any(), any())).thenReturn(List.of(hist));

        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        List<AgentGoalHistoryResponse> result = agentGoalImpl.getHistory(1L, 10L, from, to);

        assertThat(result).hasSize(1);
    }

    @Test
    void getHistory_specificAgent_withoutDateRange_queriesAllHistory() {
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        AgentGoalHistory hist = buildHistory(1L, 1L, 10L, 5.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(buildAgent(10L, "1001")));
        when(goalRepo.findByAdminId(1L)).thenReturn(List.of(goal));
        when(goalRepo.findHistoryByAgent(10L)).thenReturn(List.of(hist));

        List<AgentGoalHistoryResponse> result = agentGoalImpl.getHistory(1L, 10L, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActualValue()).isEqualTo(5.0);
    }

    @Test
    void getHistory_allAgents_fetchesFromRepo() {
        when(agentRepo.findByAdminId(1L)).thenReturn(List.of(buildAgent(10L, "1001")));
        when(agentRepo.findById(10L)).thenReturn(Optional.of(buildAgent(10L, "1001")));
        when(goalRepo.findByAdminId(1L)).thenReturn(List.of());
        when(goalRepo.findHistoryByAgent(10L)).thenReturn(List.of());

        List<AgentGoalHistoryResponse> result = agentGoalImpl.getHistory(1L, null, null, null);

        assertThat(result).isEmpty();
    }

    // ─── getMyGoals ───────────────────────────────────────────────────────────

    @Test
    void getMyGoals_agentNotFound_throwsRuntimeException() {
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGoalImpl.getMyGoals(99L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Agente");
    }

    @Test
    void getMyGoals_totalCallsKpi_computesFromDashboard() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        goal.setTargetValue(5.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(8L);

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActualValue()).isEqualTo(8.0);
        assertThat(result.get(0).getAchieved()).isTrue();
    }

    @Test
    void getMyGoals_answeredCallsKpi_computesFromDashboard() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.ANSWERED_CALLS, GoalPeriod.WEEKLY);
        goal.setTargetValue(5.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(dashboardRepo.countAnsweredCalls(eq("1001"), any(), any())).thenReturn(3L);

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(3.0);
        assertThat(result.get(0).getAchieved()).isFalse();
    }

    @Test
    void getMyGoals_answerRateKpi_computesCorrectly() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.ANSWER_RATE, GoalPeriod.MONTHLY);
        goal.setTargetValue(80.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(10L);
        when(dashboardRepo.countAnsweredCalls(eq("1001"), any(), any())).thenReturn(9L);

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(90.0);
        assertThat(result.get(0).getAchieved()).isTrue();
    }

    @Test
    void getMyGoals_answerRateKpi_zeroTotalCalls_returnsZero() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.ANSWER_RATE, GoalPeriod.DAILY);
        goal.setTargetValue(80.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(0L);

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(0.0);
    }

    @Test
    void getMyGoals_typifiedCallsKpi_countsTypifications() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TYPIFIED_CALLS, GoalPeriod.DAILY);
        goal.setTargetValue(3.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(typificationRepo.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of(
                buildTypification("C1", 10L, "SALE"),
                buildTypification("C2", 10L, "CALLBACK")));

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(2.0);
    }

    @Test
    void getMyGoals_conversionsKpi_countsSalesOnly() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.CONVERSIONS, GoalPeriod.DAILY);
        goal.setTargetValue(2.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(typificationRepo.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of(
                buildTypification("C1", 10L, "SALE"),
                buildTypification("C2", 10L, "CALLBACK"),
                buildTypification("C3", 10L, "SALE")));

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(2.0);
    }

    @Test
    void getMyGoals_conversionRateKpi_computesRate() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.CONVERSION_RATE, GoalPeriod.DAILY);
        goal.setTargetValue(50.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(typificationRepo.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of(
                buildTypification("C1", 10L, "SALE"),
                buildTypification("C2", 10L, "CALLBACK")));

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(50.0);
    }

    @Test
    void getMyGoals_conversionRateKpi_emptyTypifications_returnsZero() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.CONVERSION_RATE, GoalPeriod.DAILY);
        goal.setTargetValue(50.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(typificationRepo.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of());

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(0.0);
    }

    @Test
    void getMyGoals_appointmentsKpi_countsAppointments() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.APPOINTMENTS, GoalPeriod.WEEKLY);
        goal.setTargetValue(2.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(typificationRepo.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of(
                buildTypification("C1", 10L, "APPOINTMENT"),
                buildTypification("C2", 10L, "SALE")));

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(1.0);
    }

    @Test
    void getMyGoals_callbacksHandledKpi_countsCallbacks() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.CALLBACKS_HANDLED, GoalPeriod.DAILY);
        goal.setTargetValue(2.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(typificationRepo.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of(
                buildTypification("C1", 10L, "CALLBACK"),
                buildTypification("C2", 10L, "CALLBACK"),
                buildTypification("C3", 10L, "SALE")));

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(2.0);
    }

    @Test
    void getMyGoals_avgCallDurationKpi_computesAverage() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.AVG_CALL_DURATION, GoalPeriod.DAILY);
        goal.setTargetValue(5.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(2L);
        when(dashboardRepo.sumDurationSeconds(eq("1001"), any(), any())).thenReturn(600.0);

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(300.0);
    }

    @Test
    void getMyGoals_avgCallDurationKpi_zeroTotal_returnsZero() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.AVG_CALL_DURATION, GoalPeriod.DAILY);
        goal.setTargetValue(5.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(0L);

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getActualValue()).isEqualTo(0.0);
    }

    @Test
    void getMyGoals_zeroTarget_progressIsZero() {
        Agent agent = buildAgent(10L, "1001");
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        goal.setTargetValue(0.0);
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(goalRepo.findActiveGoalsForAgent(1L, 10L)).thenReturn(List.of(goal));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(5L);

        List<GoalProgressResponse> result = agentGoalImpl.getMyGoals(10L, 1L);

        assertThat(result.get(0).getProgressPercent()).isEqualTo(0.0);
    }

    // ─── evaluateDailyGoals ────────────────────────────────────────────────────

    @Test
    void evaluateDailyGoals_newHistory_createsSnapshot() {
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        Agent agent = buildAgent(10L, "1001");
        AgentGoalHistory saved = buildHistory(1L, 1L, 10L, 5.0);

        when(goalRepo.findByPeriod(GoalPeriod.DAILY)).thenReturn(List.of(goal));
        when(goalRepo.findByPeriod(GoalPeriod.WEEKLY)).thenReturn(List.of());
        when(goalRepo.findByPeriod(GoalPeriod.MONTHLY)).thenReturn(List.of());
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(5L);
        when(goalRepo.findHistoryByGoalAndAgentAndDate(eq(1L), eq(10L), any())).thenReturn(Optional.empty());
        when(goalRepo.saveHistory(any())).thenReturn(saved);

        List<AgentGoalHistoryResponse> result = agentGoalImpl.evaluateDailyGoals();

        assertThat(result).hasSize(1);
        verify(goalRepo).saveHistory(any());
    }

    @Test
    void evaluateDailyGoals_existingHistory_updatesSnapshot() {
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);
        Agent agent = buildAgent(10L, "1001");
        AgentGoalHistory existing = buildHistory(1L, 1L, 10L, 3.0);
        AgentGoalHistory updated = buildHistory(1L, 1L, 10L, 7.0);

        when(goalRepo.findByPeriod(GoalPeriod.DAILY)).thenReturn(List.of(goal));
        when(goalRepo.findByPeriod(GoalPeriod.WEEKLY)).thenReturn(List.of());
        when(goalRepo.findByPeriod(GoalPeriod.MONTHLY)).thenReturn(List.of());
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(7L);
        when(goalRepo.findHistoryByGoalAndAgentAndDate(eq(1L), eq(10L), any())).thenReturn(Optional.of(existing));
        when(goalRepo.saveHistory(any())).thenReturn(updated);

        List<AgentGoalHistoryResponse> result = agentGoalImpl.evaluateDailyGoals();

        assertThat(result).hasSize(1);
    }

    @Test
    void evaluateDailyGoals_agentNull_skipsAgent() {
        AgentGoal goal = buildGoal(1L, 1L, 10L, GoalKpi.TOTAL_CALLS, GoalPeriod.DAILY);

        when(goalRepo.findByPeriod(GoalPeriod.DAILY)).thenReturn(List.of(goal));
        when(goalRepo.findByPeriod(GoalPeriod.WEEKLY)).thenReturn(List.of());
        when(goalRepo.findByPeriod(GoalPeriod.MONTHLY)).thenReturn(List.of());
        when(agentRepo.findById(10L)).thenReturn(Optional.empty());

        List<AgentGoalHistoryResponse> result = agentGoalImpl.evaluateDailyGoals();

        assertThat(result).isEmpty();
        verify(goalRepo, never()).saveHistory(any());
    }

    @Test
    void evaluateDailyGoals_goalForAllAgents_iteratesAllAdmin() {
        AgentGoal goal = buildGoal(1L, 1L, null, GoalKpi.TOTAL_CALLS, GoalPeriod.WEEKLY);
        goal.setAgentId(null);
        Agent a1 = buildAgent(10L, "1001");
        AgentGoalHistory saved = buildHistory(1L, 1L, 10L, 4.0);

        when(goalRepo.findByPeriod(GoalPeriod.DAILY)).thenReturn(List.of());
        when(goalRepo.findByPeriod(GoalPeriod.WEEKLY)).thenReturn(List.of(goal));
        when(goalRepo.findByPeriod(GoalPeriod.MONTHLY)).thenReturn(List.of());
        when(agentRepo.findByAdminId(1L)).thenReturn(List.of(a1));
        when(agentRepo.findById(10L)).thenReturn(Optional.of(a1));
        when(dashboardRepo.countTotalCalls(eq("1001"), any(), any())).thenReturn(4L);
        when(goalRepo.findHistoryByGoalAndAgentAndDate(any(), eq(10L), any())).thenReturn(Optional.empty());
        when(goalRepo.saveHistory(any())).thenReturn(saved);

        List<AgentGoalHistoryResponse> result = agentGoalImpl.evaluateDailyGoals();

        assertThat(result).hasSize(1);
    }
}
