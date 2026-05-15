package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.models.Appointment;
import com.monitor.call.domain.ports.out.AppointmentRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AppointmentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AppointmentJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AppointmentRepositoryImpl implements AppointmentRepositoryPort {

    private final AppointmentJpaRepository repo;

    public AppointmentRepositoryImpl(AppointmentJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public Appointment save(Appointment appointment) {
        return toModel(repo.save(toEntity(appointment)));
    }

    @Override
    public Optional<Appointment> findById(Long id) {
        return repo.findById(id).map(this::toModel);
    }

    @Override
    public List<Appointment> findByLeadId(Long leadId) {
        return repo.findByLeadId(leadId).stream().map(this::toModel).toList();
    }

    @Override
    public List<Appointment> findAllByAgent(Long agentId) {
        return repo.findAllByAgent(agentId).stream().map(this::toModel).toList();
    }

    @Override
    public List<Appointment> findAllByAgents(List<Long> agentIds) {
        return repo.findAllByAgents(agentIds).stream().map(this::toModel).toList();
    }

    private Appointment toModel(AppointmentEntity e) {
        return Appointment.builder()
                .id(e.getId())
                .leadId(e.getLeadId())
                .agentId(e.getAgentId())
                .callId(e.getCallId())
                .appointmentDate(e.getAppointmentDate())
                .appointmentTime(e.getAppointmentTime())
                .address(e.getAddress())
                .attendees(e.getAttendees())
                .notes(e.getNotes())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private AppointmentEntity toEntity(Appointment a) {
        return AppointmentEntity.builder()
                .id(a.getId())
                .leadId(a.getLeadId())
                .agentId(a.getAgentId())
                .callId(a.getCallId())
                .appointmentDate(a.getAppointmentDate())
                .appointmentTime(a.getAppointmentTime())
                .address(a.getAddress())
                .attendees(a.getAttendees())
                .notes(a.getNotes())
                .status(a.getStatus() != null ? a.getStatus() : AppointmentStatus.SCHEDULED)
                .build();
    }
}
