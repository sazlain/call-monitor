package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.CallResult;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.exceptions.NotFoundException;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.ports.in.AppointmentUseCases;
import com.monitor.call.domain.ports.in.CallTypificationUseCases;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.CallTypificationRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.infrastructure.mappers.LeadMapper;
import com.monitor.call.infrastructure.requests.CallTypificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class CallTypificationImpl implements CallTypificationUseCases {

    private static final Logger logger = LoggerFactory.getLogger(CallTypificationImpl.class);

    private final CallTypificationRepositoryPort typRepo;
    private final LeadUseCases leadUseCases;
    private final AppointmentUseCases appointmentUseCases;
    private final AgentRepositoryPort agentRepo;
    private final UserRepositoryPort userRepo;

    public CallTypificationImpl(CallTypificationRepositoryPort typRepo,
                                LeadUseCases leadUseCases,
                                AppointmentUseCases appointmentUseCases,
                                AgentRepositoryPort agentRepo,
                                UserRepositoryPort userRepo) {
        this.typRepo = typRepo;
        this.leadUseCases = leadUseCases;
        this.appointmentUseCases = appointmentUseCases;
        this.agentRepo = agentRepo;
        this.userRepo = userRepo;
    }

    @Override
    @Transactional
    public CallTypificationResponse typify(CallTypificationRequest request, Long agentId) {
        if (typRepo.existsByCallId(request.getCallId())) {
            return updateTypification(request.getCallId(), request, agentId);
        }

        CallTypification typification = CallTypification.builder()
                .callId(request.getCallId())
                .agentId(agentId)
                .leadId(request.getLeadId())
                .result(request.getResult())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .notes(request.getNotes())
                .callbackDate(request.getCallbackDate())
                .build();

        CallTypification saved = typRepo.save(typification);
        logger.info("Llamada tipificada: callId={} result={} agentId={}",
                request.getCallId(), request.getResult(), agentId);

        if (request.getLeadId() != null) {
            updateLeadStatusFromResult(request.getLeadId(), request.getResult(),
                    request.getCallbackDate());
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public CallTypificationResponse updateTypification(String callId, CallTypificationRequest request, Long agentId) {
        CallTypification existing = typRepo.findByCallId(callId)
                .orElseThrow(() -> new NotFoundException("Tipificacion no encontrada para callId: " + callId));

        existing.setResult(request.getResult());
        existing.setContactName(request.getContactName());
        existing.setContactPhone(request.getContactPhone());
        existing.setNotes(request.getNotes());
        existing.setCallbackDate(request.getCallbackDate());

        CallTypification updated = typRepo.save(existing);

        if (existing.getLeadId() != null) {
            updateLeadStatusFromResult(existing.getLeadId(), request.getResult(), request.getCallbackDate());
        }

        return toResponse(updated);
    }

    @Override
    public CallTypificationResponse getByCallId(String callId) {
        return typRepo.findByCallId(callId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Tipificacion no encontrada: " + callId));
    }

    @Override
    public List<CallTypificationResponse> listByAgent(Long agentId) {
        return typRepo.findByAgentId(agentId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<CallTypificationResponse> listByLead(Long leadId) {
        return typRepo.findByLeadId(leadId).stream()
                .sorted(Comparator.comparing(CallTypification::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    /**
     * Mapea CallResult -> LeadStatus y actualiza el lead automáticamente.
     */
    private void updateLeadStatusFromResult(Long leadId, CallResult result, java.time.LocalDate callbackDate) {
        LeadStatus newStatus = switch (result) {
            case SALE                                  -> LeadStatus.CONVERTED;
            case INTERESTED                            -> LeadStatus.INTERESTED;
            case CALLBACK                              -> LeadStatus.CALLBACK;
            case APPOINTMENT, APPOINTMENT_RESCHEDULE   -> LeadStatus.APPOINTMENT;
            case APPOINTMENT_CANCEL                    -> LeadStatus.CANCELLED;
            case NOT_INTERESTED, WRONG_NUMBER          -> LeadStatus.DISCARDED;
            case NO_ANSWER, VOICEMAIL, OTHER           -> LeadStatus.CONTACTED;
        };
        leadUseCases.updateLeadStatus(leadId, newStatus, callbackDate);
        logger.info("Lead {} actualizado a {} por resultado {}", leadId, newStatus, result);

        if (result == CallResult.APPOINTMENT_CANCEL) {
            appointmentUseCases.cancelLatestByLeadId(leadId);
        }
    }

    private String resolveAgentName(Long agentId) {
        return agentRepo.findById(agentId)
                .map(agent -> agent.getUserName() != null
                        ? agent.getUserName()
                        : userRepo.findById(agent.getUserId())
                                  .map(u -> u.getName())
                                  .orElse("Desconocido"))
                .orElseGet(() -> userRepo.findById(agentId)
                        .map(u -> u.getName())
                        .orElse("Desconocido"));
    }

    private CallTypificationResponse toResponse(CallTypification t) {
        return LeadMapper.typDomainToResponse(t, resolveAgentName(t.getAgentId()));
    }
}
