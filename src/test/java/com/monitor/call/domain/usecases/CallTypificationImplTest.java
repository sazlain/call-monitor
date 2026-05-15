package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.CallResult;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.in.AppointmentUseCases;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.CallTypificationRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.infrastructure.requests.CallTypificationRequest;
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
class CallTypificationImplTest {

    @Mock private CallTypificationRepositoryPort typRepo;
    @Mock private LeadUseCases leadUseCases;
    @Mock private AppointmentUseCases appointmentUseCases;
    @Mock private AgentRepositoryPort agentRepo;
    @Mock private UserRepositoryPort userRepo;

    @InjectMocks
    private CallTypificationImpl typificationImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CallTypificationRequest buildRequest(String callId, CallResult result, Long leadId) {
        return CallTypificationRequest.builder()
                .callId(callId).result(result).leadId(leadId)
                .contactName("Contact").contactPhone("5551234").notes("note")
                .build();
    }

    private CallTypification buildSaved(Long id, String callId, Long agentId, CallResult result) {
        return CallTypification.builder()
                .id(id).callId(callId).agentId(agentId)
                .result(result).contactName("Contact").contactPhone("5551234")
                .notes("note").createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private void stubAgent(Long agentId, Long userId) {
        Agent agent = Agent.builder().id(agentId).userId(userId)
                .extension("1001").active(true).userName("Agent Name").build();
        when(agentRepo.findById(agentId)).thenReturn(Optional.of(agent));
    }

    // ─── typify ──────────────────────────────────────────────────────────────────

    @Test
    void typify_newCallId_savesTypificationAndReturnsResponse() {
        CallTypificationRequest req = buildRequest("CALL-001", CallResult.SALE, null);
        when(typRepo.existsByCallId("CALL-001")).thenReturn(false);
        CallTypification saved = buildSaved(1L, "CALL-001", 10L, CallResult.SALE);
        when(typRepo.save(any())).thenReturn(saved);
        stubAgent(10L, 100L);

        CallTypificationResponse resp = typificationImpl.typify(req, 10L);

        assertThat(resp.getCallId()).isEqualTo("CALL-001");
        assertThat(resp.getResult()).isEqualTo(CallResult.SALE);
        verify(typRepo).save(any());
    }

    @Test
    void typify_existingCallId_updatesInsteadOfInsert() {
        CallTypificationRequest req = buildRequest("CALL-001", CallResult.INTERESTED, null);
        when(typRepo.existsByCallId("CALL-001")).thenReturn(true);
        CallTypification existing = buildSaved(1L, "CALL-001", 10L, CallResult.SALE);
        when(typRepo.findByCallId("CALL-001")).thenReturn(Optional.of(existing));
        when(typRepo.save(any())).thenReturn(existing);
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(typRepo, atLeastOnce()).save(any());
    }

    @Test
    void typify_withLeadId_updatesLeadStatus() {
        CallTypificationRequest req = buildRequest("CALL-002", CallResult.SALE, 5L);
        when(typRepo.existsByCallId("CALL-002")).thenReturn(false);
        CallTypification saved = buildSaved(2L, "CALL-002", 10L, CallResult.SALE);
        when(typRepo.save(any())).thenReturn(saved);
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(eq(5L), eq(LeadStatus.CONVERTED), any());
    }

    @Test
    void typify_withCallbackResult_updatesLeadToCallback() {
        CallTypificationRequest req = buildRequest("CALL-003", CallResult.CALLBACK, 7L);
        req.setCallbackDate(LocalDate.now().plusDays(2));
        when(typRepo.existsByCallId("CALL-003")).thenReturn(false);
        CallTypification saved = buildSaved(3L, "CALL-003", 10L, CallResult.CALLBACK);
        when(typRepo.save(any())).thenReturn(saved);
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(eq(7L), eq(LeadStatus.CALLBACK), any());
    }

    @Test
    void typify_noLeadId_doesNotUpdateLeadStatus() {
        CallTypificationRequest req = buildRequest("CALL-004", CallResult.NO_ANSWER, null);
        when(typRepo.existsByCallId("CALL-004")).thenReturn(false);
        CallTypification saved = buildSaved(4L, "CALL-004", 10L, CallResult.NO_ANSWER);
        when(typRepo.save(any())).thenReturn(saved);
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases, never()).updateLeadStatus(any(), any(), any());
    }

    @Test
    void typify_appointmentCancel_cancelsLatestAppointment() {
        CallTypificationRequest req = buildRequest("CALL-005", CallResult.APPOINTMENT_CANCEL, 8L);
        when(typRepo.existsByCallId("CALL-005")).thenReturn(false);
        when(typRepo.save(any())).thenReturn(buildSaved(5L, "CALL-005", 10L, CallResult.APPOINTMENT_CANCEL));
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(appointmentUseCases).cancelLatestByLeadId(8L);
        verify(leadUseCases).updateLeadStatus(8L, LeadStatus.CANCELLED, null);
    }

    // ─── updateTypification ───────────────────────────────────────────────────────

    @Test
    void updateTypification_existing_updatesFieldsAndSaves() {
        CallTypification existing = buildSaved(1L, "CALL-001", 10L, CallResult.SALE);
        existing.setLeadId(null);
        when(typRepo.findByCallId("CALL-001")).thenReturn(Optional.of(existing));
        when(typRepo.save(any())).thenReturn(existing);
        stubAgent(10L, 100L);

        CallTypificationRequest req = buildRequest("CALL-001", CallResult.INTERESTED, null);
        typificationImpl.updateTypification("CALL-001", req, 10L);

        verify(typRepo).save(argThat(t -> t.getResult() == CallResult.INTERESTED));
    }

    @Test
    void updateTypification_notFound_throwsRuntimeException() {
        when(typRepo.findByCallId("NOT-FOUND")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> typificationImpl.updateTypification(
                "NOT-FOUND", buildRequest("NOT-FOUND", CallResult.OTHER, null), 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tipificacion");
    }

    // ─── getByCallId ──────────────────────────────────────────────────────────────

    @Test
    void getByCallId_exists_returnsResponse() {
        CallTypification typ = buildSaved(1L, "CALL-001", 10L, CallResult.SALE);
        when(typRepo.findByCallId("CALL-001")).thenReturn(Optional.of(typ));
        stubAgent(10L, 100L);

        CallTypificationResponse resp = typificationImpl.getByCallId("CALL-001");

        assertThat(resp.getCallId()).isEqualTo("CALL-001");
    }

    @Test
    void getByCallId_notFound_throwsRuntimeException() {
        when(typRepo.findByCallId("BAD-CALL")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> typificationImpl.getByCallId("BAD-CALL"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Tipificacion");
    }

    // ─── listByAgent / listByLead ─────────────────────────────────────────────────

    @Test
    void listByAgent_returnsAllTypificationsForAgent() {
        CallTypification t1 = buildSaved(1L, "C-1", 10L, CallResult.SALE);
        CallTypification t2 = buildSaved(2L, "C-2", 10L, CallResult.CALLBACK);
        when(typRepo.findByAgentId(10L)).thenReturn(List.of(t1, t2));
        stubAgent(10L, 100L);

        List<CallTypificationResponse> result = typificationImpl.listByAgent(10L);

        assertThat(result).hasSize(2);
    }

    @Test
    void listByLead_returnsAllTypificationsForLead() {
        CallTypification t1 = buildSaved(1L, "C-1", 10L, CallResult.INTERESTED);
        when(typRepo.findByLeadId(5L)).thenReturn(List.of(t1));
        stubAgent(10L, 100L);

        List<CallTypificationResponse> result = typificationImpl.listByLead(5L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listByLead_empty_returnsEmptyList() {
        when(typRepo.findByLeadId(99L)).thenReturn(List.of());

        List<CallTypificationResponse> result = typificationImpl.listByLead(99L);

        assertThat(result).isEmpty();
    }

    // ─── lead status mapping ──────────────────────────────────────────────────────

    @Test
    void typify_SALE_updatesLeadToConverted() {
        CallTypificationRequest req = buildRequest("C-SALE", CallResult.SALE, 1L);
        when(typRepo.existsByCallId("C-SALE")).thenReturn(false);
        when(typRepo.save(any())).thenReturn(buildSaved(1L, "C-SALE", 10L, CallResult.SALE));
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(1L, LeadStatus.CONVERTED, null);
    }

    @Test
    void typify_NOT_INTERESTED_updatesLeadToDiscarded() {
        CallTypificationRequest req = buildRequest("C-NI", CallResult.NOT_INTERESTED, 2L);
        when(typRepo.existsByCallId("C-NI")).thenReturn(false);
        when(typRepo.save(any())).thenReturn(buildSaved(2L, "C-NI", 10L, CallResult.NOT_INTERESTED));
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(2L, LeadStatus.DISCARDED, null);
    }

    @Test
    void typify_APPOINTMENT_updatesLeadToAppointment() {
        CallTypificationRequest req = buildRequest("C-APT", CallResult.APPOINTMENT, 3L);
        when(typRepo.existsByCallId("C-APT")).thenReturn(false);
        when(typRepo.save(any())).thenReturn(buildSaved(3L, "C-APT", 10L, CallResult.APPOINTMENT));
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(3L, LeadStatus.APPOINTMENT, null);
    }

    @Test
    void typify_VOICEMAIL_updatesLeadToContacted() {
        CallTypificationRequest req = buildRequest("C-VM", CallResult.VOICEMAIL, 4L);
        when(typRepo.existsByCallId("C-VM")).thenReturn(false);
        when(typRepo.save(any())).thenReturn(buildSaved(4L, "C-VM", 10L, CallResult.VOICEMAIL));
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(4L, LeadStatus.CONTACTED, null);
    }

    @Test
    void typify_INTERESTED_updatesLeadToInterested() {
        CallTypificationRequest req = buildRequest("C-INT", CallResult.INTERESTED, 5L);
        when(typRepo.existsByCallId("C-INT")).thenReturn(false);
        when(typRepo.save(any())).thenReturn(buildSaved(5L, "C-INT", 10L, CallResult.INTERESTED));
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.INTERESTED, null);
    }

    @Test
    void typify_WRONG_NUMBER_updatesLeadToDiscarded() {
        CallTypificationRequest req = buildRequest("C-WN", CallResult.WRONG_NUMBER, 6L);
        when(typRepo.existsByCallId("C-WN")).thenReturn(false);
        when(typRepo.save(any())).thenReturn(buildSaved(6L, "C-WN", 10L, CallResult.WRONG_NUMBER));
        stubAgent(10L, 100L);

        typificationImpl.typify(req, 10L);

        verify(leadUseCases).updateLeadStatus(6L, LeadStatus.DISCARDED, null);
    }
}
