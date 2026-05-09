package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.CallResult;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.ports.in.CallTypificationUseCases;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.ports.out.CallTypificationRepositoryPort;
import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.mappers.LeadMapper;
import com.monitor.call.infrastructure.requests.CallTypificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CallTypificationImpl implements CallTypificationUseCases {

    private static final Logger logger = LoggerFactory.getLogger(CallTypificationImpl.class);

    private final CallTypificationRepositoryPort typRepo;
    private final LeadUseCases leadUseCases;
    private final UserJpaRepository userRepo;
    private final AgentJpaRepository agentRepo;

    public CallTypificationImpl(CallTypificationRepositoryPort typRepo,
                                LeadUseCases leadUseCases,
                                UserJpaRepository userRepo,
                                AgentJpaRepository agentRepo) {
        this.typRepo = typRepo;
        this.leadUseCases = leadUseCases;
        this.userRepo = userRepo;
        this.agentRepo = agentRepo;
    }

    @Override
    @Transactional
    public CallTypificationResponse typify(CallTypificationRequest request, Long agentId) {
        if (typRepo.existsByCallId(request.getCallId()))
            throw new RuntimeException("La llamada " + request.getCallId() + " ya fue tipificada");

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
        logger.info("Llamada tipificada: callId={} result={} agentId={}", request.getCallId(), request.getResult(), agentId);

        // Actualizar status del lead automaticamente segun el resultado
        if (request.getLeadId() != null) {
            updateLeadStatusFromResult(request.getLeadId(), request.getResult(),
                    request.getCallbackDate() != null ? request.getCallbackDate() : null);
        }

        return toResponse(saved);
    }

    @Override
    @Transactional
    public CallTypificationResponse updateTypification(String callId, CallTypificationRequest request, Long agentId) {
        CallTypification existing = typRepo.findByCallId(callId)
                .orElseThrow(() -> new RuntimeException("Tipificacion no encontrada para callId: " + callId));

        existing.setResult(request.getResult());
        existing.setContactName(request.getContactName());
        existing.setContactPhone(request.getContactPhone());
        existing.setNotes(request.getNotes());
        existing.setCallbackDate(request.getCallbackDate());

        CallTypification updated = typRepo.save(existing);

        // Re-actualizar lead si corresponde
        if (existing.getLeadId() != null) {
            updateLeadStatusFromResult(existing.getLeadId(), request.getResult(), request.getCallbackDate());
        }

        return toResponse(updated);
    }

    @Override
    public CallTypificationResponse getByCallId(String callId) {
        return typRepo.findByCallId(callId)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Tipificacion no encontrada: " + callId));
    }

    @Override
    public List<CallTypificationResponse> listByAgent(Long agentId) {
        return typRepo.findByAgentId(agentId).stream().map(this::toResponse).toList();
    }

    @Override
    public List<CallTypificationResponse> listByLead(Long leadId) {
        return typRepo.findByLeadId(leadId).stream().map(this::toResponse).toList();
    }

    /**
     * Mapea CallResult -> LeadStatus y actualiza el lead automaticamente.
     * Este es el puente entre el resultado de la llamada y el estado del lead.
     */
    private void updateLeadStatusFromResult(Long leadId, CallResult result, java.time.LocalDate callbackDate) {
        LeadStatus newStatus = switch (result) {
            case SALE         -> LeadStatus.CONVERTED;
            case INTERESTED   -> LeadStatus.INTERESTED;
            case CALLBACK     -> LeadStatus.CALLBACK;
            case NOT_INTERESTED, WRONG_NUMBER -> LeadStatus.DISCARDED;
            case NO_ANSWER, VOICEMAIL, OTHER  -> LeadStatus.CONTACTED;
        };
        leadUseCases.updateLeadStatus(leadId, newStatus, callbackDate);
        logger.info("Lead {} actualizado a {} por resultado {}", leadId, newStatus, result);
    }

    private CallTypificationResponse toResponse(CallTypification t) {
        String agentName = agentRepo.findById(t.getAgentId())
                .flatMap(a -> userRepo.findById(a.getUserId()))
                .map(u -> u.getName()).orElse("Desconocido");
        return LeadMapper.typDomainToResponse(t, agentName);
    }
}
