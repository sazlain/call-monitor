package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.models.Appointment;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AppointmentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.AppointmentRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AppointmentJpaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentRepositoryImplTest {

    @Mock private AppointmentJpaRepository repo;

    @InjectMocks private AppointmentRepositoryImpl appointmentRepo;

    private AppointmentEntity buildEntity(Long id, Long leadId, Long agentId) {
        return AppointmentEntity.builder()
                .id(id).leadId(leadId).agentId(agentId)
                .callId("CALL-" + id)
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(10, 0))
                .address("Calle 1").attendees(2).notes("note")
                .status(AppointmentStatus.SCHEDULED)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private Appointment buildDomain(Long id, Long leadId, Long agentId) {
        return Appointment.builder()
                .id(id).leadId(leadId).agentId(agentId)
                .callId("CALL-" + id)
                .appointmentDate(LocalDate.now().plusDays(1))
                .appointmentTime(LocalTime.of(10, 0))
                .address("Calle 1").attendees(2).notes("note")
                .status(AppointmentStatus.SCHEDULED)
                .build();
    }

    // ─── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_mapsToEntityAndBack() {
        AppointmentEntity entity = buildEntity(1L, 5L, 10L);
        when(repo.save(any())).thenReturn(entity);

        Appointment result = appointmentRepo.save(buildDomain(null, 5L, 10L));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getLeadId()).isEqualTo(5L);
        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(repo).save(any());
    }

    @Test
    void save_nullStatus_defaultsToScheduled() {
        Appointment domain = buildDomain(null, 5L, 10L);
        domain.setStatus(null);
        AppointmentEntity entity = buildEntity(1L, 5L, 10L);
        when(repo.save(any())).thenReturn(entity);

        appointmentRepo.save(domain);

        verify(repo).save(argThat(e -> e.getStatus() == AppointmentStatus.SCHEDULED));
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsMappedAppointment() {
        when(repo.findById(1L)).thenReturn(Optional.of(buildEntity(1L, 5L, 10L)));

        Optional<Appointment> result = appointmentRepo.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getLeadId()).isEqualTo(5L);
        assertThat(result.get().getCallId()).isEqualTo("CALL-1");
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(appointmentRepo.findById(99L)).isEmpty();
    }

    // ─── findByLeadId ─────────────────────────────────────────────────────────

    @Test
    void findByLeadId_returnsAllAppointmentsForLead() {
        when(repo.findByLeadId(5L)).thenReturn(List.of(
                buildEntity(1L, 5L, 10L),
                buildEntity(2L, 5L, 10L)));

        List<Appointment> result = appointmentRepo.findByLeadId(5L);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByLeadId_empty_returnsEmptyList() {
        when(repo.findByLeadId(99L)).thenReturn(List.of());

        assertThat(appointmentRepo.findByLeadId(99L)).isEmpty();
    }

    // ─── findAllByAgent ───────────────────────────────────────────────────────

    @Test
    void findAllByAgent_returnsAppointmentsForAgent() {
        when(repo.findAllByAgent(10L)).thenReturn(List.of(buildEntity(1L, 5L, 10L)));

        List<Appointment> result = appointmentRepo.findAllByAgent(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAgentId()).isEqualTo(10L);
    }

    // ─── findAllByAgents ──────────────────────────────────────────────────────

    @Test
    void findAllByAgents_returnsAppointmentsForMultipleAgents() {
        when(repo.findAllByAgents(List.of(10L, 11L))).thenReturn(List.of(
                buildEntity(1L, 5L, 10L),
                buildEntity(2L, 6L, 11L)));

        List<Appointment> result = appointmentRepo.findAllByAgents(List.of(10L, 11L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findAllByAgents_empty_returnsEmptyList() {
        when(repo.findAllByAgents(List.of())).thenReturn(List.of());

        assertThat(appointmentRepo.findAllByAgents(List.of())).isEmpty();
    }
}
