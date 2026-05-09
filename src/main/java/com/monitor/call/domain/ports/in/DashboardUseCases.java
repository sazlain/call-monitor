package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.*;
import java.time.OffsetDateTime;

public interface DashboardUseCases {
    AgentDashboardResponse getAgentDashboard(String extension, OffsetDateTime from, OffsetDateTime to);
    AdminDashboardResponse getAdminDashboard(Long adminId, OffsetDateTime from, OffsetDateTime to, Long groupId);
    AgentStatusResponse getAgentStatus(Long adminId, Long groupId);
    SalesDashboardResponse getSalesDashboard(Long ownerId, OffsetDateTime from, OffsetDateTime to);
}
