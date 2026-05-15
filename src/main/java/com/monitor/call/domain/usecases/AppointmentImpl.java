package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.AppointmentStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.exceptions.NotFoundException;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.Appointment;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.in.AppointmentUseCases;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class AppointmentImpl implements AppointmentUseCases {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentImpl.class);

    private final AppointmentRepositoryPort appointmentRepo;
    private final LeadRepositoryPort        leadRepo;
    private final AgentRepositoryPort       agentRepo;
    private final UserRepositoryPort        userRepo;
    private final LeadUseCases             leadUseCases;
    private final SystemConfigUseCases     configUseCases;
    private final EmailService             emailService;
    private final EmailTemplates           emailTemplates;

    public AppointmentImpl(AppointmentRepositoryPort appointmentRepo,
                           LeadRepositoryPort leadRepo,
                           AgentRepositoryPort agentRepo,
                           UserRepositoryPort userRepo,
                           LeadUseCases leadUseCases,
                           SystemConfigUseCases configUseCases,
                           EmailService emailService,
                           EmailTemplates emailTemplates) {
        this.appointmentRepo = appointmentRepo;
        this.leadRepo        = leadRepo;
        this.agentRepo       = agentRepo;
        this.userRepo        = userRepo;
        this.leadUseCases    = leadUseCases;
        this.configUseCases  = configUseCases;
        this.emailService    = emailService;
        this.emailTemplates  = emailTemplates;
    }

    @Override
    @Transactional
    public AppointmentResponse create(AppointmentRequest request, Long agentId) {
        Appointment appointment = Appointment.builder()
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

        Appointment saved = appointmentRepo.save(appointment);
        logger.info("Cita agendada: leadId={} date={} agentId={}",
                request.getLeadId(), request.getAppointmentDate(), agentId);

        if (request.getLeadId() != null) {
            leadUseCases.updateLeadStatus(request.getLeadId(), LeadStatus.APPOINTMENT, null);
        }

        sendAppointmentAlert(saved, agentId);
        return toResponse(saved);
    }

    /**
     * Solo la cita con ID mayor (más reciente) del lead puede modificarse.
     */
    private void assertIsLastAppointment(Long leadId, Long appointmentId) {
        if (leadId == null) return;
        long maxId = appointmentRepo.findByLeadId(leadId).stream()
                .mapToLong(Appointment::getId)
                .max()
                .orElse(appointmentId);
        if (maxId != appointmentId) {
            throw new IllegalStateException("Solo la cita más reciente del lead puede modificarse.");
        }
    }

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long appointmentId, AppointmentRequest request) {
        Appointment existing = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(existing.getLeadId(), appointmentId);

        existing.setStatus(AppointmentStatus.RESCHEDULED);
        appointmentRepo.save(existing);

        Appointment newAppt = Appointment.builder()
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

        Appointment saved = appointmentRepo.save(newAppt);
        logger.info("Cita reagendada: leadId={} newDate={}", existing.getLeadId(), request.getAppointmentDate());

        if (existing.getLeadId() != null) {
            leadUseCases.updateLeadStatus(existing.getLeadId(), LeadStatus.APPOINTMENT_RESCHEDULED, null);
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public AppointmentResponse cancel(Long appointmentId, String reason) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(appointment.getLeadId(), appointmentId);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        if (reason != null) {
            String prev = appointment.getNotes();
            appointment.setNotes((prev != null ? prev + " | " : "") + "Cancelada: " + reason);
        }
        appointmentRepo.save(appointment);

        leadUseCases.updateLeadStatus(appointment.getLeadId(), LeadStatus.CANCELLED, null);
        logger.info("Cita cancelada: appointmentId={} leadId={}", appointmentId, appointment.getLeadId());
        return toResponse(appointment);
    }

    @Override
    @Transactional
    public AppointmentResponse confirm(Long appointmentId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(appointment.getLeadId(), appointmentId);

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepo.save(appointment);

        if (appointment.getLeadId() != null) {
            leadUseCases.updateLeadStatus(appointment.getLeadId(), LeadStatus.CONFIRMED, null);
        }
        logger.info("Cita confirmada: appointmentId={} leadId={} -> CONFIRMED",
                appointmentId, appointment.getLeadId());
        return toResponse(appointment);
    }

    @Override
    public List<AppointmentResponse> listMyAppointments(Long agentId) {
        return appointmentRepo.findAllByAgent(agentId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public List<AppointmentResponse> listAll(Long adminId) {
        List<Long> agentIds = agentRepo.findByAdminId(adminId).stream()
                .map(Agent::getId).toList();
        if (agentIds.isEmpty()) return List.of();
        return appointmentRepo.findAllByAgents(agentIds).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public List<AppointmentResponse> listByLead(Long leadId) {
        return appointmentRepo.findByLeadId(leadId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void cancelLatestByLeadId(Long leadId) {
        if (leadId == null) return;
        appointmentRepo.findByLeadId(leadId).stream()
                .max(Comparator.comparingLong(Appointment::getId))
                .ifPresent(appt -> {
                    if (appt.getStatus() != AppointmentStatus.CANCELLED
                            && appt.getStatus() != AppointmentStatus.ATTENDED) {
                        appt.setStatus(AppointmentStatus.CANCELLED);
                        appointmentRepo.save(appt);
                        logger.info("Cita {} cancelada automáticamente (leadId={})", appt.getId(), leadId);
                    }
                });
    }

    @Override
    public AppointmentResponse findById(Long appointmentId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Cita no encontrada: " + appointmentId));
        return toResponse(appointment);
    }

    @Override
    @Transactional
    public AppointmentResponse markRescheduled(Long appointmentId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(appointment.getLeadId(), appointmentId);
        appointment.setStatus(AppointmentStatus.RESCHEDULED);
        appointmentRepo.save(appointment);
        if (appointment.getLeadId() != null) {
            leadUseCases.updateLeadStatus(appointment.getLeadId(), LeadStatus.APPOINTMENT_RESCHEDULED, null);
        }
        logger.info("Cita marcada como reagendada: appointmentId={}", appointmentId);
        return toResponse(appointment);
    }

    @Override
    @Transactional
    public AppointmentResponse reactivate(Long appointmentId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(appointment.getLeadId(), appointmentId);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointmentRepo.save(appointment);
        if (appointment.getLeadId() != null) {
            leadUseCases.updateLeadStatus(appointment.getLeadId(), LeadStatus.APPOINTMENT, null);
        }
        logger.info("Cita reactivada a SCHEDULED: appointmentId={}", appointmentId);
        return toResponse(appointment);
    }

    @Override
    @Transactional
    public AppointmentResponse attend(Long appointmentId) {
        Appointment appointment = appointmentRepo.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException("Cita no encontrada: " + appointmentId));
        assertIsLastAppointment(appointment.getLeadId(), appointmentId);

        appointment.setStatus(AppointmentStatus.ATTENDED);
        appointmentRepo.save(appointment);

        if (appointment.getLeadId() != null) {
            leadUseCases.updateLeadStatus(appointment.getLeadId(), LeadStatus.ATTENDED, null);
        }
        logger.info("Cita atendida: appointmentId={} -> ATTENDED", appointmentId);
        return toResponse(appointment);
    }

    // ── Mapeo a response ──────────────────────────────────────────────────────

    private AppointmentResponse toResponse(Appointment a) {
        String agentName = agentRepo.findById(a.getAgentId())
                .map(agent -> agent.getUserName() != null
                        ? agent.getUserName()
                        : userRepo.findById(agent.getUserId()).map(u -> u.getName()).orElse("Desconocido"))
                .orElse("Desconocido");

        Lead lead = a.getLeadId() != null
                ? leadRepo.findById(a.getLeadId()).orElse(null) : null;

        return AppointmentResponse.builder()
                .id(a.getId())
                .leadId(a.getLeadId())
                .agentId(a.getAgentId())
                .agentName(agentName)
                .callId(a.getCallId())
                .contactName(lead != null  ? lead.getContactName()  : null)
                .contactPhone(lead != null ? lead.getContactPhone() : null)
                .leadSource(lead != null   ? lead.getLeadSource()   : null)
                .appointmentDate(a.getAppointmentDate())
                .appointmentTime(a.getAppointmentTime())
                .address(a.getAddress())
                .attendees(a.getAttendees())
                .notes(a.getNotes())
                .status(a.getStatus())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    // ── Alerta por email ──────────────────────────────────────────────────────

    private void sendAppointmentAlert(Appointment appt, Long agentId) {
        try {
            Agent agent = agentRepo.findById(agentId).orElse(null);
            if (agent == null || agent.getAdminId() == null) return;

            Long adminId = agent.getAdminId();
            if (!configUseCases.getBooleanValue(adminId, "alerts.appointment_email")) return;

            String adminEmail = userRepo.findById(adminId)
                    .map(u -> u.getEmail()).orElse(null);
            if (adminEmail == null || adminEmail.isBlank()) return;

            String agentName = agent.getUserName() != null
                    ? agent.getUserName()
                    : userRepo.findById(agent.getUserId()).map(u -> u.getName()).orElse("Agente " + agentId);

            Lead lead = appt.getLeadId() != null
                    ? leadRepo.findById(appt.getLeadId()).orElse(null) : null;

            String html = emailTemplates.newAppointment(
                    agentName,
                    lead != null ? lead.getContactName()  : null,
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
}
