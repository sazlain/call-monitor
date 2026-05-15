package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface DashboardUseCases {
    AgentDashboardResponse getAgentDashboard(String extension, OffsetDateTime from, OffsetDateTime to);
    AdminDashboardResponse getAdminDashboard(Long adminId, OffsetDateTime from, OffsetDateTime to, Long groupId, String extension);
    AgentStatusResponse getAgentStatus(Long adminId, Long groupId);
    SalesDashboardResponse getSalesDashboard(Long ownerId, OffsetDateTime from, OffsetDateTime to);
    List<ScheduleAdherenceRow> getScheduleAdherence(Long adminId, LocalDate from, LocalDate to, Long agentId);
}
