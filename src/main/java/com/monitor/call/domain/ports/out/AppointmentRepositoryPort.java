package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.models.Appointment;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepositoryPort {
    Appointment save(Appointment appointment);
    Optional<Appointment> findById(Long id);
    List<Appointment> findByLeadId(Long leadId);
    List<Appointment> findAllByAgent(Long agentId);
    List<Appointment> findAllByAgents(List<Long> agentIds);
}
