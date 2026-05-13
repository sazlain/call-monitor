package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallResult;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.*;
import com.monitor.call.domain.ports.out.*;
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

import com.monitor.call.domain.enums.CallStatus;

@ExtendWith(MockitoExtension.class)
class ReportImplTest {

    @Mock private DashboardRepositoryPort callEventPort;
    @Mock private CallTypificationRepositoryPort typPort;
    @Mock private AgentRepositoryPort agentPort;
    @Mock private AgentGroupRepositoryPort groupPort;
    @Mock private UserRepositoryPort userPort;
    @Mock private LeadRepositoryPort leadPort;

    @InjectMocks
    private ReportImpl reportImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private OffsetDateTime from() { return OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC); }
    private OffsetDateTime to()   { return OffsetDateTime.of(2026, 5, 12, 23, 59, 59, 0, ZoneOffset.UTC); }

    private CallEvent buildCallEvent(String callId, String extension, CallStatus status) {
        return CallEvent.builder()
                .callId(callId).callerExtension(extension).callerIdNum("5551001")
                .calledNumber("5550001").callerIdName("Contact")
                .callStatus(status).callFlow(CallFlow.out)
                .createdAt(OffsetDateTime.of(2026, 5, 5, 10, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }

    private CallEvent buildCallEventAt(String callId, String extension, CallStatus status, OffsetDateTime at) {
        return CallEvent.builder()
                .callId(callId).callerExtension(extension)
                .callStatus(status).callFlow(CallFlow.out)
                .createdAt(at)
                .build();
    }

    private Agent buildAgent(Long id, Long userId, String extension) {
        return Agent.builder().id(id).userId(userId).extension(extension).active(true).build();
    }

    private AgentGroup buildGroup(Long id, String name) {
        return AgentGroup.builder().id(id).name(name).active(true).agents(List.of()).build();
    }

    private CallTypification buildTypification(Long id, String callId, Long agentId, CallResult result) {
        return CallTypification.builder()
                .id(id).callId(callId).agentId(agentId).result(result)
                .contactName("Contact").contactPhone("5551001").notes("note")
                .createdAt(OffsetDateTime.of(2026, 5, 5, 11, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }

    private Lead buildLead(Long id, Long ownerId, LeadStatus status) {
        return Lead.builder()
                .id(id).ownerId(ownerId).status(status)
                .contactName("Lead " + id).contactPhone("555" + id)
                .leadSource("WEB").leadDate(LocalDate.of(2026, 5, 5))
                .build();
    }

    // ─── generateAgentCallReport ──────────────────────────────────────────────

    @Test
    void generateAgentCallReport_noEvents_returnsHeaderOnlyCsv() {
        when(callEventPort.findByCallerExtension("1001")).thenReturn(List.of());
        when(agentPort.findByExtension("1001")).thenReturn(Optional.empty());
        when(typPort.findByAgentAndPeriod(eq(-1L), any(), any())).thenReturn(List.of());

        byte[] result = reportImpl.generateAgentCallReport("1001", from(), to());

        assertThat(result).isNotNull().isNotEmpty();
        String csv = new String(result).trim();
        // BOM + header
        assertThat(csv).contains("Call ID");
        assertThat(csv).contains("Duracion");
    }

    @Test
    void generateAgentCallReport_withCallEvents_includesCallData() {
        OffsetDateTime callTime = OffsetDateTime.of(2026, 5, 5, 10, 0, 0, 0, ZoneOffset.UTC);
        CallEvent calling = buildCallEventAt("CALL-001", "1001", CallStatus.CALLING, callTime);
        CallEvent answer  = buildCallEventAt("CALL-001", "1001", CallStatus.ANSWER, callTime.plusSeconds(5));
        CallEvent hangup  = buildCallEventAt("CALL-001", "1001", CallStatus.HANGUP, callTime.plusSeconds(65));

        when(callEventPort.findByCallerExtension("1001")).thenReturn(List.of(calling, answer, hangup));
        when(agentPort.findByExtension("1001")).thenReturn(Optional.of(buildAgent(10L, 100L, "1001")));
        when(typPort.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of());

        byte[] result = reportImpl.generateAgentCallReport("1001", from(), to());

        assertThat(result).isNotNull();
        String csv = new String(result);
        assertThat(csv).contains("CALL-001");
        assertThat(csv).contains("HANGUP");
    }

    @Test
    void generateAgentCallReport_withTypification_marksAsTipificado() {
        OffsetDateTime callTime = OffsetDateTime.of(2026, 5, 5, 10, 0, 0, 0, ZoneOffset.UTC);
        CallEvent calling = buildCallEventAt("CALL-001", "1001", CallStatus.CALLING, callTime);

        when(callEventPort.findByCallerExtension("1001")).thenReturn(List.of(calling));
        when(agentPort.findByExtension("1001")).thenReturn(Optional.of(buildAgent(10L, 100L, "1001")));

        CallTypification typ = buildTypification(1L, "CALL-001", 10L, CallResult.SALE);
        when(typPort.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of(typ));

        byte[] result = reportImpl.generateAgentCallReport("1001", from(), to());

        String csv = new String(result);
        assertThat(csv).contains("Si"); // tipificado
        assertThat(csv).contains("SALE");
    }

    @Test
    void generateAgentCallReport_eventOutsideDateRange_excluded() {
        // event is before `from`
        OffsetDateTime before = OffsetDateTime.of(2026, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        CallEvent oldEvent = buildCallEventAt("CALL-OLD", "1001", CallStatus.CALLING, before);

        when(callEventPort.findByCallerExtension("1001")).thenReturn(List.of(oldEvent));
        when(agentPort.findByExtension("1001")).thenReturn(Optional.empty());
        when(typPort.findByAgentAndPeriod(eq(-1L), any(), any())).thenReturn(List.of());

        byte[] result = reportImpl.generateAgentCallReport("1001", from(), to());

        String csv = new String(result);
        assertThat(csv).doesNotContain("CALL-OLD");
    }

    // ─── generateGroupReport ──────────────────────────────────────────────────

    @Test
    void generateGroupReport_specificGroupId_usesOnlyThatGroup() {
        AgentGroup group = buildGroup(5L, "Sales");
        when(groupPort.findById(5L)).thenReturn(Optional.of(group));
        when(agentPort.findByGroupId(5L)).thenReturn(List.of());

        byte[] result = reportImpl.generateGroupReport(1L, 5L, from(), to());

        assertThat(result).isNotNull();
        String csv = new String(result);
        assertThat(csv).contains("Extension");
        assertThat(csv).contains("Ventas");
        verify(groupPort).findById(5L);
        verify(groupPort, never()).findByAdminId(any());
    }

    @Test
    void generateGroupReport_noGroupId_usesAllAdminGroups() {
        AgentGroup g1 = buildGroup(1L, "Group A");
        AgentGroup g2 = buildGroup(2L, "Group B");
        when(groupPort.findByAdminId(1L)).thenReturn(List.of(g1, g2));
        when(agentPort.findByGroupId(1L)).thenReturn(List.of());
        when(agentPort.findByGroupId(2L)).thenReturn(List.of());

        byte[] result = reportImpl.generateGroupReport(1L, null, from(), to());

        assertThat(result).isNotNull();
        verify(groupPort).findByAdminId(1L);
        verify(groupPort, never()).findById(any());
    }

    @Test
    void generateGroupReport_withAgents_includesAgentData() {
        AgentGroup group = buildGroup(5L, "Sales");
        when(groupPort.findById(5L)).thenReturn(Optional.of(group));

        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentPort.findByGroupId(5L)).thenReturn(List.of(agent));
        when(userPort.findById(100L)).thenReturn(Optional.of(
                User.builder().id(100L).name("Ana").email("ana@test.com").build()));

        OffsetDateTime callTime = OffsetDateTime.of(2026, 5, 5, 10, 0, 0, 0, ZoneOffset.UTC);
        CallEvent calling = buildCallEventAt("CALL-001", "1001", CallStatus.CALLING, callTime);
        CallEvent answer  = buildCallEventAt("CALL-001", "1001", CallStatus.ANSWER, callTime.plusSeconds(10));
        CallEvent hangup  = buildCallEventAt("CALL-001", "1001", CallStatus.HANGUP, callTime.plusSeconds(70));
        when(callEventPort.findByCallerExtension("1001")).thenReturn(List.of(calling, answer, hangup));
        when(typPort.findByAgentAndPeriod(eq(10L), any(), any())).thenReturn(List.of(
                buildTypification(1L, "CALL-001", 10L, CallResult.SALE)));

        byte[] result = reportImpl.generateGroupReport(1L, 5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("Ana");
        assertThat(csv).contains("1001");
        assertThat(csv).contains("Sales");
    }

    @Test
    void generateGroupReport_groupNotFound_returnsHeaderOnly() {
        when(groupPort.findById(99L)).thenReturn(Optional.empty());

        byte[] result = reportImpl.generateGroupReport(1L, 99L, from(), to());

        assertThat(result).isNotNull();
        // Group not found → empty groups list → only header in CSV
        String csv = new String(result);
        assertThat(csv).contains("Extension");
    }

    // ─── generateLeadReport ───────────────────────────────────────────────────

    @Test
    void generateLeadReport_noLeads_returnsHeaderOnlyCsv() {
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of());

        byte[] result = reportImpl.generateLeadReport(5L, from(), to());

        assertThat(result).isNotNull();
        String csv = new String(result);
        assertThat(csv).contains("Contacto");
        assertThat(csv).contains("Estado");
    }

    @Test
    void generateLeadReport_withLeads_includesLeadData() {
        Lead lead = buildLead(1L, 5L, LeadStatus.CONVERTED);
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(lead));
        when(typPort.findByLeadId(1L)).thenReturn(List.of());

        byte[] result = reportImpl.generateLeadReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("Lead 1");
        assertThat(csv).contains("CONVERTED");
        assertThat(csv).contains("WEB");
    }

    @Test
    void generateLeadReport_withAssignedAgent_includesAgentName() {
        Lead lead = buildLead(1L, 5L, LeadStatus.INTERESTED);
        lead.setAssignedAgentId(10L);
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(lead));

        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentPort.findById(10L)).thenReturn(Optional.of(agent));
        when(userPort.findById(100L)).thenReturn(Optional.of(
                User.builder().id(100L).name("Agent Ana").email("ana@test.com").build()));
        when(typPort.findByLeadId(1L)).thenReturn(List.of());

        byte[] result = reportImpl.generateLeadReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("Agent Ana");
    }

    @Test
    void generateLeadReport_withTypification_showsLastResult() {
        Lead lead = buildLead(1L, 5L, LeadStatus.INTERESTED);
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(lead));
        CallTypification typ = buildTypification(1L, "CALL-001", 10L, CallResult.INTERESTED);
        when(typPort.findByLeadId(1L)).thenReturn(List.of(typ));

        byte[] result = reportImpl.generateLeadReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("INTERESTED");
        assertThat(csv).contains("Si"); // tipificado
    }

    @Test
    void generateLeadReport_leadOutsideDateRange_excluded() {
        Lead lead = buildLead(1L, 5L, LeadStatus.NEW);
        lead.setLeadDate(LocalDate.of(2026, 4, 1)); // before from
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(lead));

        byte[] result = reportImpl.generateLeadReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).doesNotContain("Lead 1");
    }

    @Test
    void generateLeadReport_withCallbackDate_includesDate() {
        Lead lead = buildLead(1L, 5L, LeadStatus.CALLBACK);
        lead.setCallbackDate(LocalDate.of(2026, 5, 20));
        when(leadPort.findByOwnerId(5L)).thenReturn(List.of(lead));
        when(typPort.findByLeadId(1L)).thenReturn(List.of());

        byte[] result = reportImpl.generateLeadReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("2026-05-20");
    }

    // ─── generateCallbackReport ───────────────────────────────────────────────

    @Test
    void generateCallbackReport_noCallbacks_returnsHeaderOnlyCsv() {
        when(agentPort.findByUserId(5L)).thenReturn(Optional.empty());
        when(leadPort.findPendingCallbacks(5L, -1L)).thenReturn(List.of());

        byte[] result = reportImpl.generateCallbackReport(5L, from(), to());

        assertThat(result).isNotNull();
        String csv = new String(result);
        assertThat(csv).contains("Contacto");
        assertThat(csv).contains("Vencido");
    }

    @Test
    void generateCallbackReport_withCallbacks_includesLeadData() {
        when(agentPort.findByUserId(5L)).thenReturn(Optional.of(buildAgent(10L, 5L, "1001")));
        Lead lead = buildLead(1L, 5L, LeadStatus.CALLBACK);
        lead.setCallbackDate(LocalDate.now().plusDays(2));
        when(leadPort.findPendingCallbacks(5L, 10L)).thenReturn(List.of(lead));

        byte[] result = reportImpl.generateCallbackReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("Lead 1");
        assertThat(csv).contains("No"); // not overdue
    }

    @Test
    void generateCallbackReport_overdueCallback_marksAsVencido() {
        when(agentPort.findByUserId(5L)).thenReturn(Optional.of(buildAgent(10L, 5L, "1001")));
        Lead lead = buildLead(1L, 5L, LeadStatus.CALLBACK);
        lead.setCallbackDate(LocalDate.now().minusDays(3)); // overdue
        when(leadPort.findPendingCallbacks(5L, 10L)).thenReturn(List.of(lead));

        byte[] result = reportImpl.generateCallbackReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("Si"); // overdue = true
    }

    @Test
    void generateCallbackReport_withAssignedAgent_includesAgentName() {
        when(agentPort.findByUserId(5L)).thenReturn(Optional.empty());
        Lead lead = buildLead(1L, 5L, LeadStatus.CALLBACK);
        lead.setCallbackDate(LocalDate.now().plusDays(1));
        lead.setAssignedAgentId(20L);
        when(leadPort.findPendingCallbacks(5L, -1L)).thenReturn(List.of(lead));

        Agent agent = buildAgent(20L, 200L, "1002");
        when(agentPort.findById(20L)).thenReturn(Optional.of(agent));
        when(userPort.findById(200L)).thenReturn(Optional.of(
                User.builder().id(200L).name("Agent Bob").email("bob@test.com").build()));

        byte[] result = reportImpl.generateCallbackReport(5L, from(), to());

        String csv = new String(result);
        assertThat(csv).contains("Agent Bob");
    }

    @Test
    void generateCallbackReport_noCallbackDate_handlesNullGracefully() {
        when(agentPort.findByUserId(5L)).thenReturn(Optional.empty());
        Lead lead = buildLead(1L, 5L, LeadStatus.CALLBACK);
        lead.setCallbackDate(null); // no date set
        when(leadPort.findPendingCallbacks(5L, -1L)).thenReturn(List.of(lead));

        byte[] result = reportImpl.generateCallbackReport(5L, from(), to());

        assertThat(result).isNotNull();
        String csv = new String(result);
        assertThat(csv).contains("Lead 1");
    }
}
