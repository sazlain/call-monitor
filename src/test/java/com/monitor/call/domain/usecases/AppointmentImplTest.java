package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.Appointment;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.AppointmentRepositoryPort;
import com.monitor.call.domain.ports.out.LeadRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.AppointmentResponse;
import com.monitor.call.infrastructure.requests.AppointmentRequest;
import com.monitor.call.infrastructure.services.EmailService;
import com.monitor.call.infrastructure.services.EmailTemplates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentImplTest {

    @Mock private AppointmentRepositoryPort appointmentRepo;
    @Mock private LeadRepositoryPort leadRepo;
    @Mock private AgentRepositoryPort agentRepo;
    @Mock private UserRepositoryPort userRepo;
    @Mock private LeadUseCases leadUseCases;
    @Mock private SystemConfigUseCases configUseCases;
    @Mock private EmailService emailService;
    @Mock private EmailTemplates emailTemplates;

    @InjectMocks
    private AppointmentImpl appointmentImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private AppointmentRequest buildRequest(Long leadId, String callId) {
        return AppointmentRequest.builder()
                .leadId(leadId).callId(callId)
                .appointmentDate(LocalDate.now().plusDays(3))
                .appointmentTime(LocalTime.of(10, 0))
                .address("Calle 1").attendees(2).notes("note")
                .build();
    }

    private Appointment buildAppointment(Long id, Long leadId, Long agentId, AppointmentStatus status) {
        return Appointment.builder()
                .id(id).leadId(leadId).agentId(agentId).callId("CALL-" + id)
                .appointmentDate(LocalDate.now().plusDays(3))
                .appointmentTime(LocalTime.of(10, 0))
                .status(status).notes("note")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private Agent buildAgent(Long id, Long userId, Long adminId) {
        return Agent.builder().id(id).userId(userId).extension("100" + id)
                .active(true).adminId(adminId).userName("Agent " + id).build();
    }

    private Lead buildLead(Long id) {
        return Lead.builder().id(id).contactName("Contact").contactPhone("555")
                .leadSource("WEB").build();
    }

    private void stubToResponse(Long agentId, Long userId, Long leadId) {
        when(agentRepo.findById(agentId)).thenReturn(Optional.of(buildAgent(agentId, userId, 99L)));
        if (leadId != null) {
            when(leadRepo.findById(leadId)).thenReturn(Optional.of(buildLead(leadId)));
        }
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void create_withLeadId_savesAndUpdatesLeadStatus() {
        AppointmentRequest req = buildRequest(1L, "CALL-001");
        Appointment saved = buildAppointment(10L, 1L, 5L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(saved);
        when(agentRepo.findById(5L)).thenReturn(Optional.of(buildAgent(5L, 50L, null)));
        when(leadRepo.findById(1L)).thenReturn(Optional.of(buildLead(1L)));

        AppointmentResponse resp = appointmentImpl.create(req, 5L);

        assertThat(resp.getId()).isEqualTo(10L);
        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(appointmentRepo).save(argThat(a -> a.getAgentId().equals(5L)));
        verify(leadUseCases).updateLeadStatus(1L, LeadStatus.APPOINTMENT, null);
    }

    @Test
    void create_withoutLeadId_doesNotUpdateLead() {
        AppointmentRequest req = buildRequest(null, "CALL-002");
        Appointment saved = buildAppointment(11L, null, 5L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(saved);
        when(agentRepo.findById(5L)).thenReturn(Optional.of(buildAgent(5L, 50L, null)));

        appointmentImpl.create(req, 5L);

        verify(leadUseCases, never()).updateLeadStatus(any(), any(), any());
    }

    @Test
    void create_setsStatusToScheduled() {
        AppointmentRequest req = buildRequest(1L, "CALL-003");
        Appointment saved = buildAppointment(12L, 1L, 5L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(saved);
        when(agentRepo.findById(5L)).thenReturn(Optional.of(buildAgent(5L, 50L, null)));
        when(leadRepo.findById(1L)).thenReturn(Optional.of(buildLead(1L)));

        appointmentImpl.create(req, 5L);

        verify(appointmentRepo).save(argThat(a -> a.getStatus() == AppointmentStatus.SCHEDULED));
    }

    // ─── reschedule ───────────────────────────────────────────────────────────

    @Test
    void reschedule_marksOldAsRescheduledAndCreatesNew() {
        Appointment existing = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(existing));
        Appointment newAppt = buildAppointment(2L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(existing).thenReturn(newAppt);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.reschedule(1L, buildRequest(5L, "CALL-NEW"));

        assertThat(resp.getId()).isEqualTo(2L);
        verify(appointmentRepo, times(2)).save(any());
        verify(appointmentRepo).save(argThat(a -> a.getStatus() == AppointmentStatus.RESCHEDULED));
    }

    @Test
    void reschedule_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.reschedule(99L, buildRequest(1L, "X")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cita");
    }

    // ─── cancel ───────────────────────────────────────────────────────────────

    @Test
    void cancel_setsStatusToCancelledAndUpdatesLead() {
        Appointment appt = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(appt));
        when(appointmentRepo.save(any())).thenReturn(appt);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.cancel(1L, "reason");

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointmentRepo).save(argThat(a -> a.getStatus() == AppointmentStatus.CANCELLED));
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.CANCELLED, null);
    }

    @Test
    void cancel_withNullReason_stillCancels() {
        Appointment appt = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(appt));
        when(appointmentRepo.save(any())).thenReturn(appt);
        stubToResponse(10L, 100L, 5L);

        appointmentImpl.cancel(1L, null);

        verify(appointmentRepo).save(argThat(a -> a.getStatus() == AppointmentStatus.CANCELLED));
    }

    @Test
    void cancel_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.cancel(99L, "reason"))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── confirm ──────────────────────────────────────────────────────────────

    @Test
    void confirm_setsStatusToConfirmedAndUpdatesLead() {
        Appointment appt = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(appt));
        when(appointmentRepo.save(any())).thenReturn(appt);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.confirm(1L);

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.CONFIRMED, null);
    }

    @Test
    void confirm_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.confirm(99L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── attend ───────────────────────────────────────────────────────────────

    @Test
    void attend_setsStatusToAttendedAndUpdatesLead() {
        Appointment appt = buildAppointment(1L, 5L, 10L, AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(appt));
        when(appointmentRepo.save(any())).thenReturn(appt);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.attend(1L);

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.ATTENDED);
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.ATTENDED, null);
    }

    @Test
    void attend_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.attend(99L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── reactivate ───────────────────────────────────────────────────────────

    @Test
    void reactivate_setsStatusToScheduled() {
        Appointment appt = buildAppointment(1L, 5L, 10L, AppointmentStatus.CANCELLED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(appt));
        when(appointmentRepo.save(any())).thenReturn(appt);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.reactivate(1L);

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.APPOINTMENT, null);
    }

    // ─── markRescheduled ──────────────────────────────────────────────────────

    @Test
    void markRescheduled_setsStatusToRescheduled() {
        Appointment appt = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(appt));
        when(appointmentRepo.save(any())).thenReturn(appt);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.markRescheduled(1L);

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.APPOINTMENT_RESCHEDULED, null);
    }

    // ─── listMyAppointments ───────────────────────────────────────────────────

    @Test
    void listMyAppointments_returnsAppointmentsForAgent() {
        Appointment a1 = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        Appointment a2 = buildAppointment(2L, 6L, 10L, AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findAllByAgent(10L)).thenReturn(List.of(a1, a2));
        when(agentRepo.findById(10L)).thenReturn(Optional.of(buildAgent(10L, 100L, 99L)));
        when(leadRepo.findById(5L)).thenReturn(Optional.of(buildLead(5L)));
        when(leadRepo.findById(6L)).thenReturn(Optional.of(buildLead(6L)));

        List<AppointmentResponse> result = appointmentImpl.listMyAppointments(10L);

        assertThat(result).hasSize(2);
    }

    @Test
    void listMyAppointments_empty_returnsEmptyList() {
        when(appointmentRepo.findAllByAgent(10L)).thenReturn(List.of());

        List<AppointmentResponse> result = appointmentImpl.listMyAppointments(10L);

        assertThat(result).isEmpty();
    }

    // ─── listAll ──────────────────────────────────────────────────────────────

    @Test
    void listAll_noAgents_returnsEmptyList() {
        when(agentRepo.findByAdminId(1L)).thenReturn(List.of());

        List<AppointmentResponse> result = appointmentImpl.listAll(1L);

        assertThat(result).isEmpty();
        verify(appointmentRepo, never()).findAllByAgents(any());
    }

    @Test
    void listAll_withAgents_returnsAllAppointments() {
        Agent agent = buildAgent(10L, 100L, 1L);
        when(agentRepo.findByAdminId(1L)).thenReturn(List.of(agent));
        Appointment a = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findAllByAgents(List.of(10L))).thenReturn(List.of(a));
        stubToResponse(10L, 100L, 5L);

        List<AppointmentResponse> result = appointmentImpl.listAll(1L);

        assertThat(result).hasSize(1);
    }

    // ─── listByLead ───────────────────────────────────────────────────────────

    @Test
    void listByLead_returnsAppointmentsForLead() {
        Appointment a = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(a));
        stubToResponse(10L, 100L, 5L);

        List<AppointmentResponse> result = appointmentImpl.listByLead(5L);

        assertThat(result).hasSize(1);
    }

    // ─── cancelLatestByLeadId ─────────────────────────────────────────────────

    @Test
    void cancelLatestByLeadId_cancelsActiveAppointment() {
        Appointment active = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(active));
        when(appointmentRepo.save(any())).thenReturn(active);

        appointmentImpl.cancelLatestByLeadId(5L);

        verify(appointmentRepo).save(argThat(a -> a.getStatus() == AppointmentStatus.CANCELLED));
    }

    @Test
    void cancelLatestByLeadId_alreadyCancelled_doesNotSave() {
        Appointment cancelled = buildAppointment(1L, 5L, 10L, AppointmentStatus.CANCELLED);
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(cancelled));

        appointmentImpl.cancelLatestByLeadId(5L);

        verify(appointmentRepo, never()).save(any());
    }

    @Test
    void cancelLatestByLeadId_nullLeadId_doesNothing() {
        appointmentImpl.cancelLatestByLeadId(null);

        verify(appointmentRepo, never()).findByLeadId(any());
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsResponse() {
        Appointment a = buildAppointment(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(a));
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.findById(1L);

        assertThat(resp.getId()).isEqualTo(1L);
    }

    @Test
    void findById_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cita");
    }
}
