package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.ports.in.AppointmentUseCases;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.responses.AppointmentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AppointmentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AppointmentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.requests.AppointmentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AppointmentImpl implements AppointmentUseCases {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentImpl.class);

    private final AppointmentJpaRepository appointmentRepo;
    private final LeadJpaRepository        leadRepo;
    private final AgentJpaRepository       agentRepo;
    private final UserJpaRepository        userRepo;
    private final LeadUseCases             leadUseCases;

    public AppointmentImpl(AppointmentJpaRepository appointmentRepo,
                           LeadJpaRepository leadRepo,
                           AgentJpaRepository agentRepo,
                           UserJpaRepository userRepo,
                           LeadUseCases leadUseCases) {
        this.appointmentRepo = appointmentRepo;
        this.leadRepo        = leadRepo;
        this.agentRepo       = agentRepo;
        this.userRepo        = userRepo;
        this.leadUseCases    = leadUseCases;
    }

    @Override
    @Transactional
    public AppointmentResponse create(AppointmentRequest request, Long agentId) {
        AppointmentEntity entity = AppointmentEntity.builder()
                .leadId(request.getLeadId())
                .agentId(agentId)
                .callId(request.getCallId())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .address(request.getAddress())
                .attendees(request.getAttendees())
                .notes(request.getNotes())
                .status(AppointmentStatus.SCHEDULED)
                .build();

        AppointmentEntity saved = appointmentRepo.save(entity);
        logger.info("Cita agendada: leadId={} date={} agentId={}",
            request.getLeadId(), request.getAppointmentDate(), agentId);

        // Lead pasa a APPOINTMENT
        if (request.getLeadId() != null) {
            leadUseCases.updateLeadStatus(request.getLeadId(), LeadStatus.APPOINTMENT, null);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, AppointmentRequest request) {
        AppointmentEntity existing = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));

        // Marcar la anterior como reagendada
        existing.setStatus(AppointmentStatus.RESCHEDULED);
        appointmentRepo.save(existing);

        // Crear nueva cita
        AppointmentEntity newAppt = AppointmentEntity.builder()
                .leadId(existing.getLeadId())
                .agentId(existing.getAgentId())
                .callId(request.getCallId())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .address(request.getAddress() != null ? request.getAddress() : existing.getAddress())
                .attendees(request.getAttendees() != null ? request.getAttendees() : existing.getAttendees())
                .notes(request.getNotes())
                .status(AppointmentStatus.SCHEDULED)
                .build();

        AppointmentEntity saved = appointmentRepo.save(newAppt);
        logger.info("Cita reagendada: leadId={} newDate={}", existing.getLeadId(), request.getAppointmentDate());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, String reason) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));

        entity.setStatus(AppointmentStatus.CANCELLED);
        if (reason != null) entity.setNotes(
            (entity.getNotes() != null ? entity.getNotes() + " | " : "") + "Cancelada: " + reason
        );
        appointmentRepo.save(entity);

        // Lead vuelve a INTERESTED
        leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.INTERESTED, null);
        logger.info("Cita cancelada: appointmentId={} leadId={}", appointmentId, entity.getLeadId());

        return toResponse(entity);
    }

    @Override
    @Transactional
    public AppointmentResponse confirm(Long appointmentId) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));

        entity.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepo.save(entity);

        // Lead pasa a CONVERTED
        leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.CONVERTED, null);
        logger.info("Cita confirmada: appointmentId={} leadId={} -> CONVERTED", appointmentId, entity.getLeadId());

        return toResponse(entity);
    }

    @Override
    public List<AppointmentResponse> listMyAppointments(Long agentId) {
        return appointmentRepo.findAllByAgent(agentId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<AppointmentResponse> listAll(Long adminId) {
        List<Long> agentIds = agentRepo.findByAdminId(adminId)
                .stream().map(a -> a.getId()).toList();
        if (agentIds.isEmpty()) return List.of();
        return appointmentRepo.findAllByAgents(agentIds)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<AppointmentResponse> listByLead(Long leadId) {
        return appointmentRepo.findByLeadId(leadId)
                .stream().map(this::toResponse).toList();
    }

    private AppointmentResponse toResponse(AppointmentEntity e) {
        String agentName = agentRepo.findById(e.getAgentId())
                .flatMap(a -> userRepo.findById(a.getUserId()))
                .map(u -> u.getName()).orElse("Desconocido");

        LeadEntity lead = e.getLeadId() != null
                ? leadRepo.findById(e.getLeadId()).orElse(null) : null;

        return AppointmentResponse.builder()
                .id(e.getId())
                .leadId(e.getLeadId())
                .agentId(e.getAgentId())
                .agentName(agentName)
                .callId(e.getCallId())
                .contactName(lead != null  ? lead.getContactName()  : null)
                .contactPhone(lead != null ? lead.getContactPhone() : null)
                .leadSource(lead != null   ? lead.getLeadSource()   : null)
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

    @Override
    @Transactional
    public AppointmentResponse attend(Long appointmentId) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));

        entity.setStatus(AppointmentStatus.ATTENDED);
        appointmentRepo.save(entity);

        // Lead pasa a CONVERTED — cita exitosa
        leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.CONVERTED, null);
        logger.info("Cita atendida: appointmentId={} leadId={} -> CONVERTED",
                appointmentId, entity.getLeadId());

        return toResponse(entity);
    }
}
