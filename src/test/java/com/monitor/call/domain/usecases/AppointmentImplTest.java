package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.responses.AppointmentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AppointmentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AppointmentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.requests.AppointmentRequest;
import com.monitor.call.infrastructure.services.EmailService;
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

    @Mock private AppointmentJpaRepository appointmentRepo;
    @Mock private LeadJpaRepository leadRepo;
    @Mock private AgentJpaRepository agentRepo;
    @Mock private UserJpaRepository userRepo;
    @Mock private LeadUseCases leadUseCases;
    @Mock private SystemConfigUseCases configUseCases;
    @Mock private EmailService emailService;

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

    private AppointmentEntity buildEntity(Long id, Long leadId, Long agentId, AppointmentStatus status) {
        return AppointmentEntity.builder()
                .id(id).leadId(leadId).agentId(agentId).callId("CALL-" + id)
                .appointmentDate(LocalDate.now().plusDays(3))
                .appointmentTime(LocalTime.of(10, 0))
                .status(status).notes("note")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private AgentEntity buildAgentEntity(Long id, Long userId) {
        return AgentEntity.builder().id(id).userId(userId).extension("100" + id).active(true).build();
    }

    private UserEntity buildUserEntity(Long id, String name) {
        return UserEntity.builder().id(id).name(name).email(name + "@test.com").password("hash").active(true).build();
    }

    private LeadEntity buildLeadEntity(Long id, String contactName, String phone) {
        LeadEntity e = new LeadEntity();
        e.setId(id);
        e.setContactName(contactName);
        e.setContactPhone(phone);
        e.setLeadSource("WEB");
        return e;
    }

    private void stubToResponse(Long agentId, Long userId, Long leadId) {
        when(agentRepo.findById(agentId)).thenReturn(Optional.of(buildAgentEntity(agentId, userId)));
        when(userRepo.findById(userId)).thenReturn(Optional.of(buildUserEntity(userId, "Agent")));
        if (leadId != null) {
            when(leadRepo.findById(leadId)).thenReturn(Optional.of(buildLeadEntity(leadId, "Contact", "555")));
        }
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    void create_withLeadId_savesEntityAndUpdatesLeadStatus() {
        AppointmentRequest req = buildRequest(1L, "CALL-001");
        AppointmentEntity saved = buildEntity(10L, 1L, 5L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(saved);
        stubToResponse(5L, 50L, 1L);

        AppointmentResponse resp = appointmentImpl.create(req, 5L);

        assertThat(resp.getId()).isEqualTo(10L);
        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(appointmentRepo).save(argThat(e -> e.getAgentId().equals(5L)));
        verify(leadUseCases).updateLeadStatus(1L, LeadStatus.APPOINTMENT, null);
    }

    @Test
    void create_withoutLeadId_savesEntityWithoutUpdatingLead() {
        AppointmentRequest req = buildRequest(null, "CALL-002");
        AppointmentEntity saved = buildEntity(11L, null, 5L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(saved);
        stubToResponse(5L, 50L, null);

        AppointmentResponse resp = appointmentImpl.create(req, 5L);

        assertThat(resp.getId()).isEqualTo(11L);
        verify(leadUseCases, never()).updateLeadStatus(any(), any(), any());
    }

    @Test
    void create_setsStatusToScheduled() {
        AppointmentRequest req = buildRequest(1L, "CALL-003");
        AppointmentEntity saved = buildEntity(12L, 1L, 5L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(saved);
        stubToResponse(5L, 50L, 1L);

        appointmentImpl.create(req, 5L);

        verify(appointmentRepo).save(argThat(e -> e.getStatus() == AppointmentStatus.SCHEDULED));
    }

    // ─── reschedule ───────────────────────────────────────────────────────────

    @Test
    void reschedule_existingAppointment_marksOldAsRescheduledAndCreatesNew() {
        AppointmentEntity existing = buildEntity(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(existing));
        AppointmentEntity newAppt = buildEntity(2L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.save(any())).thenReturn(existing).thenReturn(newAppt);
        stubToResponse(10L, 100L, 5L);

        AppointmentRequest req = buildRequest(5L, "CALL-NEW");
        AppointmentResponse resp = appointmentImpl.reschedule(1L, req);

        assertThat(resp.getId()).isEqualTo(2L);
        verify(appointmentRepo, times(2)).save(any());
        verify(appointmentRepo).save(argThat(e -> e.getStatus() == AppointmentStatus.RESCHEDULED));
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
    void cancel_existingAppointment_setsStatusToCancelledAndUpdatesLead() {
        AppointmentEntity entity = buildEntity(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(appointmentRepo.save(any())).thenReturn(entity);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.cancel(1L, "reason");

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(appointmentRepo).save(argThat(e -> e.getStatus() == AppointmentStatus.CANCELLED));
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.INTERESTED, null);
    }

    @Test
    void cancel_withNullReason_stillCancels() {
        AppointmentEntity entity = buildEntity(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(appointmentRepo.save(any())).thenReturn(entity);
        stubToResponse(10L, 100L, 5L);

        appointmentImpl.cancel(1L, null);

        verify(appointmentRepo).save(argThat(e -> e.getStatus() == AppointmentStatus.CANCELLED));
    }

    @Test
    void cancel_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.cancel(99L, "reason"))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── confirm ──────────────────────────────────────────────────────────────

    @Test
    void confirm_existingAppointment_setsStatusToConfirmedAndKeepsAppointmentLead() {
        AppointmentEntity entity = buildEntity(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(appointmentRepo.save(any())).thenReturn(entity);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.confirm(1L);

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(appointmentRepo).save(argThat(e -> e.getStatus() == AppointmentStatus.CONFIRMED));
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.APPOINTMENT, null);
    }

    @Test
    void confirm_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.confirm(99L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── attend ───────────────────────────────────────────────────────────────

    @Test
    void attend_existingAppointment_setsStatusToAttendedAndKeepsAppointmentLead() {
        AppointmentEntity entity = buildEntity(1L, 5L, 10L, AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(appointmentRepo.save(any())).thenReturn(entity);
        stubToResponse(10L, 100L, 5L);

        AppointmentResponse resp = appointmentImpl.attend(1L);

        assertThat(resp.getStatus()).isEqualTo(AppointmentStatus.ATTENDED);
        verify(appointmentRepo).save(argThat(e -> e.getStatus() == AppointmentStatus.ATTENDED));
        verify(leadUseCases).updateLeadStatus(5L, LeadStatus.APPOINTMENT, null);
    }

    @Test
    void attend_notFound_throwsRuntimeException() {
        when(appointmentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentImpl.attend(99L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── listMyAppointments ───────────────────────────────────────────────────

    @Test
    void listMyAppointments_returnsAppointmentsForAgent() {
        AppointmentEntity e1 = buildEntity(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        AppointmentEntity e2 = buildEntity(2L, 6L, 10L, AppointmentStatus.CONFIRMED);
        when(appointmentRepo.findAllByAgent(10L)).thenReturn(List.of(e1, e2));
        stubToResponse(10L, 100L, 5L);
        when(leadRepo.findById(6L)).thenReturn(Optional.of(buildLeadEntity(6L, "Contact2", "556")));

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
        AgentEntity agentEntity = buildAgentEntity(10L, 100L);
        when(agentRepo.findByAdminId(1L)).thenReturn(List.of(agentEntity));
        AppointmentEntity e = buildEntity(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findAllByAgents(List.of(10L))).thenReturn(List.of(e));
        stubToResponse(10L, 100L, 5L);

        List<AppointmentResponse> result = appointmentImpl.listAll(1L);

        assertThat(result).hasSize(1);
    }

    // ─── listByLead ───────────────────────────────────────────────────────────

    @Test
    void listByLead_returnsAppointmentsForLead() {
        AppointmentEntity e = buildEntity(1L, 5L, 10L, AppointmentStatus.SCHEDULED);
        when(appointmentRepo.findByLeadId(5L)).thenReturn(List.of(e));
        stubToResponse(10L, 100L, 5L);

        List<AppointmentResponse> result = appointmentImpl.listByLead(5L);

        assertThat(result).hasSize(1);
    }
}
