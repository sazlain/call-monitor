package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.ports.in.AppointmentUseCases;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.responses.AppointmentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AppointmentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AppointmentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.requests.AppointmentRequest;
import com.monitor.call.infrastructure.services.EmailService;
import com.monitor.call.infrastructure.services.EmailTemplates;
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
    private final SystemConfigUseCases     configUseCases;
    private final EmailService             emailService;

    public AppointmentImpl(AppointmentJpaRepository appointmentRepo,
                           LeadJpaRepository leadRepo,
                           AgentJpaRepository agentRepo,
                           UserJpaRepository userRepo,
                           LeadUseCases leadUseCases,
                           SystemConfigUseCases configUseCases,
                           EmailService emailService) {
        this.appointmentRepo = appointmentRepo;
        this.leadRepo        = leadRepo;
        this.agentRepo       = agentRepo;
        this.userRepo        = userRepo;
        this.leadUseCases    = leadUseCases;
        this.configUseCases  = configUseCases;
        this.emailService    = emailService;
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

        // Alerta al admin por email
        sendAppointmentAlert(saved, agentId);

        return toResponse(saved);
    }

    /**
     * Lanza excepción si la cita indicada NO es la más reciente del lead.
     * Solo la última cita (mayor ID) puede modificarse.
     */
    private void assertIsLastAppointment(Long leadId, Long appointmentId) {
        if (leadId == null) return;
        boolean isLast = appointmentRepo.findByLeadId(leadId).stream()
                .mapToLong(a -> a.getId())
                .max()
                .orElse(appointmentId) == appointmentId;
        if (!isLast) {
            throw new IllegalStateException(
                "Solo la cita más reciente del lead puede modificarse.");
        }
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, AppointmentRequest request) {
        AppointmentEntity existing = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(existing.getLeadId(), appointmentId);

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

        // Lead pasa a APPOINTMENT_RESCHEDULED
        if (existing.getLeadId() != null) {
            leadUseCases.updateLeadStatus(existing.getLeadId(), LeadStatus.APPOINTMENT_RESCHEDULED, null);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, String reason) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(entity.getLeadId(), appointmentId);

        entity.setStatus(AppointmentStatus.CANCELLED);
        if (reason != null) entity.setNotes(
            (entity.getNotes() != null ? entity.getNotes() + " | " : "") + "Cancelada: " + reason
        );
        appointmentRepo.save(entity);

        // Lead hereda el estado CANCELLED de la cita
        leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.CANCELLED, null);
        logger.info("Cita cancelada: appointmentId={} leadId={}", appointmentId, entity.getLeadId());

        return toResponse(entity);
    }

    @Override
    @Transactional
    public AppointmentResponse confirm(Long appointmentId) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(entity.getLeadId(), appointmentId);

        entity.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepo.save(entity);

        // Lead hereda el estado CONFIRMED — el cliente confirmó asistencia
        if (entity.getLeadId() != null) {
            leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.CONFIRMED, null);
        }
        logger.info("Cita confirmada: appointmentId={} leadId={} -> CONFIRMED", appointmentId, entity.getLeadId());

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

    @Override
    @Transactional
    public void cancelLatestByLeadId(Long leadId) {
        if (leadId == null) return;
        appointmentRepo.findByLeadId(leadId).stream()
                .max(java.util.Comparator.comparingLong(AppointmentEntity::getId))
                .ifPresent(entity -> {
                    if (entity.getStatus() != AppointmentStatus.CANCELLED
                            && entity.getStatus() != AppointmentStatus.ATTENDED) {
                        entity.setStatus(AppointmentStatus.CANCELLED);
                        appointmentRepo.save(entity);
                        logger.info("Cita {} cancelada automáticamente por tipificación APPOINTMENT_CANCEL (leadId={})",
                                entity.getId(), leadId);
                    }
                });
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

    // ── Email helpers ─────────────────────────────────────────────────────────

    private void sendAppointmentAlert(AppointmentEntity appt, Long agentId) {
        try {
            AgentEntity agentEntity = agentRepo.findById(agentId).orElse(null);
            if (agentEntity == null || agentEntity.getGroup() == null) return;

            Long adminId = agentEntity.getGroup().getAdminId();
            if (!configUseCases.getBooleanValue(adminId, "alerts.appointment_email")) return;

            String adminEmail = userRepo.findById(adminId)
                    .map(u -> u.getEmail()).orElse(null);
            if (adminEmail == null || adminEmail.isBlank()) return;

            String agentName = userRepo.findById(agentEntity.getUserId())
                    .map(u -> u.getName()).orElse("Agente " + agentId);

            LeadEntity lead = appt.getLeadId() != null
                    ? leadRepo.findById(appt.getLeadId()).orElse(null) : null;

            String html = EmailTemplates.newAppointment(
                    agentName,
                    lead != null ? lead.getContactName() : null,
                    lead != null ? lead.getContactPhone() : null,
                    appt.getAppointmentDate() != null ? appt.getAppointmentDate().toString() : null,
                    appt.getAppointmentTime() != null ? appt.getAppointmentTime().toString() : null,
                    appt.getAddress(),
                    appt.getAttendees(),
                    appt.getNotes());

            emailService.send(adminEmail, "Nueva cita agendada", html);
        } catch (Exception e) {
            logger.warn("No se pudo enviar alerta de cita: {}", e.getMessage());
        }
    }

    @Override
    public AppointmentResponse findById(Long appointmentId) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));
        return toResponse(entity);
    }

    @Override
    @Transactional
    public AppointmentResponse markRescheduled(Long appointmentId) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(entity.getLeadId(), appointmentId);
        entity.setStatus(AppointmentStatus.RESCHEDULED);
        appointmentRepo.save(entity);
        if (entity.getLeadId() != null) {
            leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.APPOINTMENT_RESCHEDULED, null);
        }
        logger.info("Cita marcada como reagendada: appointmentId={} leadId={}", appointmentId, entity.getLeadId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public AppointmentResponse reactivate(Long appointmentId) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(entity.getLeadId(), appointmentId);
        entity.setStatus(AppointmentStatus.SCHEDULED);
        appointmentRepo.save(entity);
        if (entity.getLeadId() != null) {
            leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.APPOINTMENT, null);
        }
        logger.info("Cita reactivada a SCHEDULED: appointmentId={} leadId={}", appointmentId, entity.getLeadId());
        return toResponse(entity);
    }

    @Override
    @Transactional
    public AppointmentResponse attend(Long appointmentId) {
        AppointmentEntity entity = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(entity.getLeadId(), appointmentId);

        entity.setStatus(AppointmentStatus.ATTENDED);
        appointmentRepo.save(entity);

        // Lead pasa a ATTENDED — la visita fue realizada
        if (entity.getLeadId() != null) {
            leadUseCases.updateLeadStatus(entity.getLeadId(), LeadStatus.ATTENDED, null);
        }
        logger.info("Cita atendida: appointmentId={} leadId={} -> ATTENDED",
                appointmentId, entity.getLeadId());

        return toResponse(entity);
    }
}
