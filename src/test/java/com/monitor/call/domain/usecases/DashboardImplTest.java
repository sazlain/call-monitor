package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.AgentGroup;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.out.*;
import com.monitor.call.domain.responses.AdminDashboardResponse;
import com.monitor.call.domain.responses.AgentDashboardResponse;
import com.monitor.call.domain.responses.AgentStatusResponse;
import com.monitor.call.domain.responses.SalesDashboardResponse;
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
class DashboardImplTest {

    @Mock private DashboardRepositoryPort dashRepo;
    @Mock private AgentRepositoryPort agentPort;
    @Mock private AgentGroupRepositoryPort groupPort;
    @Mock private UserRepositoryPort userPort;
    @Mock private CallTypificationRepositoryPort typPort;
    @Mock private LeadRepositoryPort leadPort;

    @InjectMocks
    private DashboardImpl dashboardImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private OffsetDateTime from() { return OffsetDateTime.now(ZoneOffset.UTC).minusDays(7); }
    private OffsetDateTime to()   { return OffsetDateTime.now(ZoneOffset.UTC); }

    private Agent buildAgent(Long id, Long userId, String extension) {
        return Agent.builder().id(id).userId(userId).extension(extension).active(true).build();
    }

    private User buildUser(Long id, String name) {
        return User.builder().id(id).name(name).email(name + "@test.com").build();
    }

    private AgentGroup buildGroup(Long id, String name, Long adminId) {
        return AgentGroup.builder().id(id).name(name).adminId(adminId).active(true).agents(List.of()).build();
    }

    private Lead buildLead(Long id, Long ownerId, LeadStatus status) {
        return Lead.builder()
                .id(id).ownerId(ownerId).status(status)
                .leadDate(LocalDate.now()).leadSource("WEB")
                .contactName("Contact " + id).contactPhone("555000" + id)
                .build();
    }

    // ─── getAgentDashboard ────────────────────────────────────────────────────

    @Test
    void getAgentDashboard_validExtension_returnsResponse() {
        String ext = "1001";
        Agent agent = buildAgent(10L, 100L, ext);
        when(agentPort.findByExtension(ext)).thenReturn(Optional.of(agent));
        when(userPort.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Ana")));

        when(dashRepo.countTotalCalls(eq(ext), any(), any())).thenReturn(20L);
        when(dashRepo.countAnsweredCalls(eq(ext), any(), any())).thenReturn(15L);
        when(dashRepo.countMissedCalls(eq(ext), any(), any())).thenReturn(5L);
        when(dashRepo.countOutboundCalls(eq(ext), any(), any())).thenReturn(12L);
        when(dashRepo.countInboundCalls(eq(ext), any(), any())).thenReturn(8L);
        when(dashRepo.sumDurationSeconds(eq(ext), any(), any())).thenReturn(3000.0);
        when(dashRepo.maxDurationSeconds(eq(ext), any(), any())).thenReturn(600.0);
        when(dashRepo.minDurationSeconds(eq(ext), any(), any())).thenReturn(30.0);
        when(dashRepo.countShortCalls(eq(ext), any(), any())).thenReturn(3L);
        when(dashRepo.countLongCalls(eq(ext), any(), any())).thenReturn(1L);
        when(typPort.findByAgentId(10L)).thenReturn(List.of());
        when(typPort.countByResultForAgent(eq(10L), any(), any())).thenReturn(List.of());
        when(dashRepo.countByHour(eq(ext), any(), any())).thenReturn(List.of());
        when(dashRepo.countByDay(eq(ext), any(), any())).thenReturn(List.of());
        when(dashRepo.countByDayOfWeek(eq(ext), any(), any())).thenReturn(List.of());
        when(dashRepo.findRecentEvents(eq(List.of(ext)), eq(20))).thenReturn(List.of());
        when(dashRepo.findActiveExtensions(List.of(ext))).thenReturn(List.of(ext));

        AgentDashboardResponse resp = dashboardImpl.getAgentDashboard(ext, from(), to());

        assertThat(resp.getExtension()).isEqualTo(ext);
        assertThat(resp.getAgentName()).isEqualTo("Ana");
        assertThat(resp.getTotalCalls()).isEqualTo(20L);
        assertThat(resp.getAnsweredCalls()).isEqualTo(15L);
        assertThat(resp.getMissedCalls()).isEqualTo(5L);
        assertThat(resp.getIsActive()).isTrue();
    }

    @Test
    void getAgentDashboard_agentNotFound_throwsRuntimeException() {
        when(agentPort.findByExtension("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardImpl.getAgentDashboard("9999", from(), to()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Agente");
    }

    @Test
    void getAgentDashboard_zeroCalls_answerRateIsZero() {
        String ext = "1002";
        Agent agent = buildAgent(11L, 101L, ext);
        when(agentPort.findByExtension(ext)).thenReturn(Optional.of(agent));
        when(userPort.findById(101L)).thenReturn(Optional.empty()); // no user found → uses extension

        when(dashRepo.countTotalCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.countAnsweredCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.countMissedCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.countOutboundCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.countInboundCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.sumDurationSeconds(eq(ext), any(), any())).thenReturn(null);
        when(dashRepo.maxDurationSeconds(eq(ext), any(), any())).thenReturn(null);
        when(dashRepo.minDurationSeconds(eq(ext), any(), any())).thenReturn(null);
        when(dashRepo.countShortCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.countLongCalls(eq(ext), any(), any())).thenReturn(0L);
        when(typPort.findByAgentId(11L)).thenReturn(List.of());
        when(typPort.countByResultForAgent(eq(11L), any(), any())).thenReturn(List.of());
        when(dashRepo.countByHour(eq(ext), any(), any())).thenReturn(List.of());
        when(dashRepo.countByDay(eq(ext), any(), any())).thenReturn(List.of());
        when(dashRepo.countByDayOfWeek(eq(ext), any(), any())).thenReturn(List.of());
        when(dashRepo.findRecentEvents(eq(List.of(ext)), eq(20))).thenReturn(List.of());
        when(dashRepo.findActiveExtensions(List.of(ext))).thenReturn(List.of());

        AgentDashboardResponse resp = dashboardImpl.getAgentDashboard(ext, from(), to());

        assertThat(resp.getTotalCalls()).isZero();
        assertThat(resp.getAnswerRate()).isZero();
        assertThat(resp.getAvgDurationSeconds()).isZero();
        assertThat(resp.getIsActive()).isFalse();
    }

    @Test
    void getAgentDashboard_withResultDistribution_mapsCorrectly() {
        String ext = "1003";
        Agent agent = buildAgent(12L, 102L, ext);
        when(agentPort.findByExtension(ext)).thenReturn(Optional.of(agent));
        when(userPort.findById(102L)).thenReturn(Optional.of(buildUser(102L, "Bob")));

        when(dashRepo.countTotalCalls(eq(ext), any(), any())).thenReturn(5L);
        when(dashRepo.countAnsweredCalls(eq(ext), any(), any())).thenReturn(5L);
        when(dashRepo.countMissedCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.countOutboundCalls(eq(ext), any(), any())).thenReturn(5L);
        when(dashRepo.countInboundCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.sumDurationSeconds(eq(ext), any(), any())).thenReturn(500.0);
        when(dashRepo.maxDurationSeconds(eq(ext), any(), any())).thenReturn(200.0);
        when(dashRepo.minDurationSeconds(eq(ext), any(), any())).thenReturn(50.0);
        when(dashRepo.countShortCalls(eq(ext), any(), any())).thenReturn(0L);
        when(dashRepo.countLongCalls(eq(ext), any(), any())).thenReturn(0L);
        when(typPort.findByAgentId(12L)).thenReturn(List.of());
        // result distribution with one row: ["SALE", 3]
        List<Object[]> resultRows = List.<Object[]>of(new Object[]{"SALE", 3L});
        when(typPort.countByResultForAgent(eq(12L), any(), any())).thenReturn(resultRows);
        when(dashRepo.countByHour(eq(ext), any(), any())).thenReturn(List.of());
        when(dashRepo.countByDay(eq(ext), any(), any())).thenReturn(List.of());
        List<Object[]> dowRows = List.<Object[]>of(new Object[]{1, 3L});
        when(dashRepo.countByDayOfWeek(eq(ext), any(), any())).thenReturn(dowRows);
        when(dashRepo.findRecentEvents(eq(List.of(ext)), eq(20))).thenReturn(List.of());
        when(dashRepo.findActiveExtensions(List.of(ext))).thenReturn(List.of(ext));

        AgentDashboardResponse resp = dashboardImpl.getAgentDashboard(ext, from(), to());

        assertThat(resp.getResultDistribution()).hasSize(1);
        assertThat(resp.getResultDistribution().get(0).getResult()).isEqualTo("SALE");
        assertThat(resp.getResultDistribution().get(0).getCount()).isEqualTo(3L);
        assertThat(resp.getCallsByDayOfWeek()).hasSize(1);
    }

    // ─── getAdminDashboard ────────────────────────────────────────────────────

    @Test
    void getAdminDashboard_noGroups_returnsEmptyResponse() {
        when(groupPort.findByAdminId(1L)).thenReturn(List.of());

        AdminDashboardResponse resp = dashboardImpl.getAdminDashboard(1L, from(), to(), null);

        assertThat(resp.getAdminEmail()).isEmpty();
        assertThat(resp).isNotNull();
    }

    @Test
    void getAdminDashboard_withSpecificGroupId_filtersToThatGroup() {
        AgentGroup group = buildGroup(5L, "Sales", 1L);
        when(groupPort.findById(5L)).thenReturn(Optional.of(group));
        when(agentPort.findExtensionsByGroupId(5L)).thenReturn(List.of());

        AdminDashboardResponse resp = dashboardImpl.getAdminDashboard(1L, from(), to(), 5L);

        assertThat(resp).isNotNull();
    }

    @Test
    void getAdminDashboard_withGroupsAndExtensions_returnsAggregatedStats() {
        AgentGroup group = buildGroup(5L, "Sales", 1L);
        when(groupPort.findByAdminId(1L)).thenReturn(List.of(group));
        when(agentPort.findExtensionsByGroupId(5L)).thenReturn(List.of("1001"));

        // summary row: [extension, total, answered, missed]
        List<Object[]> summary1 = List.<Object[]>of(new Object[]{"1001", 10L, 8L, 2L});
        when(dashRepo.getCallSummaryByExtensions(anyList(), any(), any()))
                .thenReturn(summary1);
        when(dashRepo.sumDurationByExtensions(anyList(), any(), any())).thenReturn(800.0);
        when(dashRepo.findActiveExtensions(anyList())).thenReturn(List.of("1001"));
        when(dashRepo.sumDurationSeconds(eq("1001"), any(), any())).thenReturn(800.0);
        when(dashRepo.findLongActiveCalls(anyList(), anyLong())).thenReturn(List.of());
        when(dashRepo.findInactiveExtensions(anyList(), any())).thenReturn(List.of());
        when(dashRepo.countByDayAndExtension(anyList(), any(), any())).thenReturn(List.of());

        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentPort.findByExtension("1001")).thenReturn(Optional.of(agent));
        when(userPort.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Ana")));
        when(userPort.findById(1L)).thenReturn(Optional.of(buildUser(1L, "Admin")));
        when(typPort.countByResultForAgent(eq(10L), any(), any())).thenReturn(List.of());

        AdminDashboardResponse resp = dashboardImpl.getAdminDashboard(1L, from(), to(), null);

        assertThat(resp.getTotalCalls()).isEqualTo(10L);
        assertThat(resp.getAnsweredCalls()).isEqualTo(8L);
        assertThat(resp.getMissedCalls()).isEqualTo(2L);
        assertThat(resp.getAgentRanking()).hasSize(1);
        assertThat(resp.getAdminEmail()).isEqualTo("Admin@test.com");
    }

    @Test
    void getAdminDashboard_withAlerts_includedInResponse() {
        AgentGroup group = buildGroup(5L, "Ops", 1L);
        when(groupPort.findByAdminId(1L)).thenReturn(List.of(group));
        when(agentPort.findExtensionsByGroupId(5L)).thenReturn(List.of("2001"));

        List<Object[]> summary2 = List.<Object[]>of(new Object[]{"2001", 0L, 0L, 0L});
        when(dashRepo.getCallSummaryByExtensions(anyList(), any(), any()))
                .thenReturn(summary2);
        when(dashRepo.sumDurationByExtensions(any(), any(), any())).thenReturn(null);
        when(dashRepo.findActiveExtensions(anyList())).thenReturn(List.of());
        when(dashRepo.sumDurationSeconds(eq("2001"), any(), any())).thenReturn(null);
        when(dashRepo.countByDayAndExtension(anyList(), any(), any())).thenReturn(List.of());
        // alerts
        when(dashRepo.findLongActiveCalls(anyList(), anyLong())).thenReturn(List.of("2001"));
        when(dashRepo.findInactiveExtensions(anyList(), any())).thenReturn(List.of("2001"));

        Agent agent = buildAgent(20L, 200L, "2001");
        when(agentPort.findByExtension("2001")).thenReturn(Optional.of(agent));
        when(userPort.findById(200L)).thenReturn(Optional.empty());
        when(userPort.findById(1L)).thenReturn(Optional.empty());
        when(typPort.countByResultForAgent(eq(20L), any(), any())).thenReturn(List.of());

        AdminDashboardResponse resp = dashboardImpl.getAdminDashboard(1L, from(), to(), null);

        assertThat(resp.getAlerts()).hasSize(2);
    }

    // ─── getAgentStatus ────────────────────────────────────────────────────────

    @Test
    void getAgentStatus_withGroupId_usesGroupExtensions() {
        when(agentPort.findExtensionsByGroupId(5L)).thenReturn(List.of("1001"));
        when(dashRepo.findActiveExtensions(List.of("1001"))).thenReturn(List.of("1001"));
        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentPort.findByExtension("1001")).thenReturn(Optional.of(agent));
        when(userPort.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Ana")));
        when(dashRepo.findLastEventByExtension("1001")).thenReturn(Optional.empty());

        AgentStatusResponse resp = dashboardImpl.getAgentStatus(1L, 5L);

        assertThat(resp.getAgents()).hasSize(1);
        assertThat(resp.getTotalActive()).isEqualTo(1);
        assertThat(resp.getTotalIdle()).isEqualTo(0);
        verify(agentPort).findExtensionsByGroupId(5L);
        verify(agentPort, never()).findExtensionsByAdminId(any());
    }

    @Test
    void getAgentStatus_withoutGroupId_usesAdminExtensions() {
        when(agentPort.findExtensionsByAdminId(1L)).thenReturn(List.of("1001", "1002"));
        when(dashRepo.findActiveExtensions(List.of("1001", "1002"))).thenReturn(List.of("1001"));

        Agent a1 = buildAgent(10L, 100L, "1001");
        Agent a2 = buildAgent(11L, 101L, "1002");
        when(agentPort.findByExtension("1001")).thenReturn(Optional.of(a1));
        when(agentPort.findByExtension("1002")).thenReturn(Optional.of(a2));
        when(userPort.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Ana")));
        when(userPort.findById(101L)).thenReturn(Optional.of(buildUser(101L, "Bob")));
        when(dashRepo.findLastEventByExtension("1001")).thenReturn(Optional.empty());
        when(dashRepo.findLastEventByExtension("1002")).thenReturn(Optional.empty());

        AgentStatusResponse resp = dashboardImpl.getAgentStatus(1L, null);

        assertThat(resp.getAgents()).hasSize(2);
        assertThat(resp.getTotalActive()).isEqualTo(1);
        assertThat(resp.getTotalIdle()).isEqualTo(1);
    }

    @Test
    void getAgentStatus_noExtensions_returnsEmptyResponse() {
        when(agentPort.findExtensionsByAdminId(1L)).thenReturn(List.of());
        when(dashRepo.findActiveExtensions(List.of())).thenReturn(List.of());

        AgentStatusResponse resp = dashboardImpl.getAgentStatus(1L, null);

        assertThat(resp.getAgents()).isEmpty();
        assertThat(resp.getTotalActive()).isZero();
    }

    @Test
    void getAgentStatus_activeAgent_includesCallInfo() {
        when(agentPort.findExtensionsByAdminId(1L)).thenReturn(List.of("1001"));
        when(dashRepo.findActiveExtensions(List.of("1001"))).thenReturn(List.of("1001"));

        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentPort.findByExtension("1001")).thenReturn(Optional.of(agent));
        when(userPort.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Ana")));

        CallEvent lastEvent = CallEvent.builder()
                .callId("CALL-999").callStatus(CallStatus.CALLING).callFlow(CallFlow.out)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                .build();
        when(dashRepo.findLastEventByExtension("1001")).thenReturn(Optional.of(lastEvent));

        AgentStatusResponse resp = dashboardImpl.getAgentStatus(1L, null);

        assertThat(resp.getAgents()).hasSize(1);
        AgentStatusResponse.AgentCurrentStatus status = resp.getAgents().get(0);
        assertThat(status.getCurrentCallId()).isEqualTo("CALL-999");
        assertThat(status.getCallDurationSeconds()).isGreaterThan(0L);
    }

    // ─── getSalesDashboard ────────────────────────────────────────────────────

    @Test
    void getSalesDashboard_noLeads_returnsZeroCounts() {
        when(userPort.findById(5L)).thenReturn(Optional.of(buildUser(5L, "Owner")));
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of());

        SalesDashboardResponse resp = dashboardImpl.getSalesDashboard(5L, from(), to());

        assertThat(resp.getTotalLeads()).isZero();
        assertThat(resp.getConversionRate()).isZero();
        assertThat(resp.getAssignedAgents()).isEmpty();
    }

    @Test
    void getSalesDashboard_withLeads_countsCorrectlyByStatus() {
        when(userPort.findById(5L)).thenReturn(Optional.of(buildUser(5L, "Owner")));

        Lead newLead = buildLead(1L, 5L, LeadStatus.NEW);
        Lead convertedLead = buildLead(2L, 5L, LeadStatus.CONVERTED);
        Lead pendingLead = buildLead(3L, 5L, LeadStatus.PENDING);
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(newLead, convertedLead, pendingLead));

        SalesDashboardResponse resp = dashboardImpl.getSalesDashboard(5L, from(), to());

        assertThat(resp.getTotalLeads()).isEqualTo(3L);
        assertThat(resp.getConvertedLeads()).isEqualTo(1L);
        assertThat(resp.getNewLeads()).isEqualTo(1L);
        assertThat(resp.getPendingLeads()).isEqualTo(1L);
    }

    @Test
    void getSalesDashboard_withAssignedAgent_buildsSummaryForAgent() {
        when(userPort.findById(5L)).thenReturn(Optional.of(buildUser(5L, "Owner")));

        Lead lead1 = buildLead(1L, 5L, LeadStatus.CONVERTED);
        lead1.setAssignedAgentId(10L);
        Lead lead2 = buildLead(2L, 5L, LeadStatus.INTERESTED);
        lead2.setAssignedAgentId(10L);
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(lead1, lead2));

        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentPort.findById(10L)).thenReturn(Optional.of(agent));
        when(userPort.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Agent Ana")));

        SalesDashboardResponse resp = dashboardImpl.getSalesDashboard(5L, from(), to());

        assertThat(resp.getAssignedAgents()).hasSize(1);
        assertThat(resp.getAssignedAgents().get(0).getAgentName()).isEqualTo("Agent Ana");
        assertThat(resp.getAssignedAgents().get(0).getAssignedLeads()).isEqualTo(2L);
        assertThat(resp.getAssignedAgents().get(0).getConvertedLeads()).isEqualTo(1L);
    }

    @Test
    void getSalesDashboard_bySource_groupsCorrectly() {
        when(userPort.findById(5L)).thenReturn(Optional.of(buildUser(5L, "Owner")));

        Lead webLead1 = buildLead(1L, 5L, LeadStatus.CONVERTED);
        webLead1.setLeadSource("WEB");
        Lead webLead2 = buildLead(2L, 5L, LeadStatus.NEW);
        webLead2.setLeadSource("WEB");
        Lead refLead = buildLead(3L, 5L, LeadStatus.INTERESTED);
        refLead.setLeadSource("REF");
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(webLead1, webLead2, refLead));

        SalesDashboardResponse resp = dashboardImpl.getSalesDashboard(5L, from(), to());

        assertThat(resp.getLeadsBySource()).hasSize(2);
        // WEB first (higher total)
        assertThat(resp.getLeadsBySource().get(0).getSource()).isEqualTo("WEB");
        assertThat(resp.getLeadsBySource().get(0).getTotal()).isEqualTo(2L);
        assertThat(resp.getLeadsBySource().get(0).getConverted()).isEqualTo(1L);
    }

    @Test
    void getSalesDashboard_withCallbackLeads_countsPendingAndOverdue() {
        when(userPort.findById(5L)).thenReturn(Optional.of(buildUser(5L, "Owner")));

        Lead futureCallback = buildLead(1L, 5L, LeadStatus.CALLBACK);
        futureCallback.setCallbackDate(LocalDate.now().plusDays(2));
        Lead overdueCallback = buildLead(2L, 5L, LeadStatus.CALLBACK);
        overdueCallback.setCallbackDate(LocalDate.now().minusDays(1));
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(futureCallback, overdueCallback));

        SalesDashboardResponse resp = dashboardImpl.getSalesDashboard(5L, from(), to());

        assertThat(resp.getPendingCallbacks()).isEqualTo(1L);
        assertThat(resp.getOverdueCallbacks()).isEqualTo(1L);
    }
}
