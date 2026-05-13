package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import com.monitor.call.domain.responses.AgentGoalHistoryResponse;
import com.monitor.call.domain.responses.AgentGoalResponse;
import com.monitor.call.domain.responses.GoalProgressResponse;

import java.time.LocalDate;
import java.util.List;

public interface AgentGoalUseCases {

    /** Admin crea una meta */
    AgentGoalResponse createGoal(Long adminId, Long agentId,
                                 GoalKpi kpiType, GoalPeriod period, Double targetValue);

    /** Admin actualiza una meta */
    AgentGoalResponse updateGoal(Long goalId, Double targetValue, Long adminId);

    /** Admin desactiva una meta */
    void deactivateGoal(Long goalId, Long adminId);

    /** Admin lista sus metas activas (opcionalmente filtrando por agente) */
    List<AgentGoalResponse> listGoals(Long adminId, Long agentId);

    /** Admin consulta historial, filtros opcionales */
    List<AgentGoalHistoryResponse> getHistory(Long adminId, Long agentId,
                                              LocalDate from, LocalDate to);

    /** Agente ve sus metas con progreso actual */
    List<GoalProgressResponse> getMyGoals(Long agentId, Long adminId);

    /** Evaluación de cierre de día: guarda historial y retorna lista de no cumplidas */
    List<AgentGoalHistoryResponse> evaluateDailyGoals();
}
