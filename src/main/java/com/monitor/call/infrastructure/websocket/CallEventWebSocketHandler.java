package com.monitor.call.infrastructure.websocket;

import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.ports.out.LeadRepositoryPort;
import com.monitor.call.domain.responses.CallEventWebSocketMessage;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.services.EmailService;
import com.monitor.call.infrastructure.services.EmailTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Emite eventos de llamada por WebSocket a los canales correspondientes.
 * <p>
 * Canales:
 * /topic/calls/agent/{extension}   -> el agente ve sus propios eventos en tiempo real
 * /topic/calls/group/{groupId}     -> el admin ve todos los eventos de su grupo
 * <p>
 * Acciones que el frontend debe ejecutar segun callStatus:
 * CALLING  -> abrir formulario de captura de datos del contacto
 * ANSWER   -> iniciar cronometro de duracion de llamada
 * HANGUP   -> abrir formulario de tipificacion
 * BUSY / NOANSWER / CANCEL / CONGESTION / CHANUNAVAIL -> cerrar formulario, registrar intento fallido
 */
@Component
public class CallEventWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(CallEventWebSocketHandler.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final AgentJpaRepository agentJpaRepository;
    private final LeadRepositoryPort leadRepo;
    private final SystemConfigUseCases configUseCases;
    private final EmailService emailService;
    private final UserJpaRepository userRepo;

    public CallEventWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                                     AgentJpaRepository agentJpaRepository,
                                     LeadRepositoryPort leadRepo,
                                     SystemConfigUseCases configUseCases,
                                     EmailService emailService,
                                     UserJpaRepository userRepo) {
        this.messagingTemplate = messagingTemplate;
        this.agentJpaRepository = agentJpaRepository;
        this.leadRepo = leadRepo;
        this.configUseCases = configUseCases;
        this.emailService = emailService;
        this.userRepo = userRepo;
    }

    /**
     * Emite el evento al canal del agente y al canal del grupo al que pertenece.
     * Se llama desde CallEventListenerImpl despues de guardar el evento en BD.
     */
    public void emit(CallEvent callEvent) {
        String rawExtension = callEvent.getCallerExtension();
        String extension = normalizeExtension(rawExtension);

        AgentEntity agent = agentJpaRepository.findByExtension(extension).orElse(null);
        Long adminId = (agent != null && agent.getGroup() != null) ? agent.getGroup().getAdminId() : null;

        CallEventWebSocketMessage message = buildMessage(callEvent, adminId);

        String agentChannel = "/topic/calls/agent/" + extension;
        messagingTemplate.convertAndSend(agentChannel, message);
        logger.info("WS emitido a {}: callId={} status={}", agentChannel,
                callEvent.getCallId(), callEvent.getCallStatus());

        if (agent != null && agent.getGroup() != null) {
            String groupChannel = "/topic/calls/group/" + agent.getGroup().getId();
            messagingTemplate.convertAndSend(groupChannel, message);
        }
    }

    private String normalizeExtension(String rawExtension) {
        if (rawExtension == null || rawExtension.isBlank()) return rawExtension;
        if (agentJpaRepository.findByExtension(rawExtension).isPresent()) return rawExtension;
        for (int len = 4; len <= rawExtension.length(); len++) {
            String suffix = rawExtension.substring(rawExtension.length() - len);
            if (agentJpaRepository.findByExtension(suffix).isPresent()) {
                logger.info("Extensión normalizada: {} -> {}", rawExtension, suffix);
                return suffix;
            }
        }
        return rawExtension;
    }

    private CallEventWebSocketMessage buildMessage(CallEvent callEvent, Long adminId) {

        String phone = "out".equalsIgnoreCase(String.valueOf(callEvent.getCallFlow()))
                ? callEvent.getCalledNumber()
                : callEvent.getCallerIdNum();

        Lead lead = null;
        if (phone != null && !phone.isBlank()) {
            lead = leadRepo.findActiveByPhone(phone).orElse(null);
            logger.info("Búsqueda de lead: phone='{}' flow={} status={} -> {}",
                    phone, callEvent.getCallFlow(), callEvent.getCallStatus(),
                    lead != null ? "ENCONTRADO id=" + lead.getId() + " phone=" + lead.getContactPhone() : "NO ENCONTRADO");
        }

        // Cuando el número es desconocido, leer config para decidir comportamiento
        boolean createLeadEnabled = adminId == null
                || configUseCases.getBooleanValue(adminId, "leads.unknown.create_lead");

        // Alerta de número desconocido — cuando create_lead está desactivado
        if (callEvent.getCallStatus() == CallStatus.CALLING && lead == null && !createLeadEnabled) {
            sendUnknownCallAlert(callEvent, callEvent.getCallerExtension(), adminId);
        }

        return CallEventWebSocketMessage.builder()
                .callId(callEvent.getCallId())
                .callerExtension(callEvent.getCallerExtension())
                .callerIdNum(callEvent.getCallerIdNum())
                .callerIdName(callEvent.getCallerIdName())
                .calledNumber(callEvent.getCalledNumber())
                .calledExtension(callEvent.getCalledExtension())
                .callStatus(callEvent.getCallStatus())
                .callFlow(callEvent.getCallFlow())
                .callAPIID(callEvent.getCallAPIID())
                .timestamp(OffsetDateTime.now())
                .frontendAction(resolveFrontendAction(callEvent.getCallStatus(), lead, createLeadEnabled))
                .leadId(lead != null ? lead.getId() : null)
                .leadContactName(lead != null ? lead.getContactName() : null)
                .leadContactPhone(lead != null ? lead.getContactPhone() : null)
                .leadNotes(lead != null ? lead.getNotes() : null)
                .leadStatus(lead != null ? lead.getStatus().name() : null)
                .build();
    }

    /**
     * Traduce el CallStatus a una accion clara para el frontend.
     * CALLING + lead conocido  → OPEN_LEAD_INFO
     * CALLING + desconocido + createLeadEnabled → OPEN_CONTACT_FORM
     * CALLING + desconocido + !createLeadEnabled → SKIP_UNKNOWN (sin modal)
     * HANGUP  + desconocido + createLeadEnabled → ASK_CREATE_LEAD
     * HANGUP  + desconocido + !createLeadEnabled → REGISTER_FAILED_ATTEMPT
     */
    private String resolveFrontendAction(CallStatus status, Lead lead, boolean createLeadEnabled) {
        return switch (status) {
            case CALLING -> lead != null ? "OPEN_LEAD_INFO"
                    : createLeadEnabled ? "OPEN_CONTACT_FORM" : "SKIP_UNKNOWN";
            case ANSWER -> "START_CALL_TIMER";
            case HANGUP -> lead != null ? "OPEN_TYPIFICATION_FORM"
                    : createLeadEnabled ? "ASK_CREATE_LEAD" : "REGISTER_FAILED_ATTEMPT";
            case BUSY, NOANSWER, CANCEL, CONGESTION, CHANUNAVAIL -> "REGISTER_FAILED_ATTEMPT";
        };
    }

    /**
     * Envía alerta al admin cuando se llama a un número sin lead y create_lead está desactivado.
     */
    void sendUnknownCallAlert(CallEvent callEvent, String callerExtension, Long adminId) {
        try {
            AgentEntity agentEntity = agentJpaRepository.findByExtension(callerExtension).orElse(null);
            if (agentEntity == null || agentEntity.getGroup() == null) return;

            Long resolvedAdminId = adminId != null ? adminId : agentEntity.getGroup().getAdminId();
            if (!configUseCases.getBooleanValue(resolvedAdminId, "alerts.unknown_call_email")) return;

            String adminEmail = userRepo.findById(resolvedAdminId)
                    .map(u -> u.getEmail()).orElse(null);
            if (adminEmail == null || adminEmail.isBlank()) return;

            String agentName = userRepo.findById(agentEntity.getUserId())
                    .map(u -> u.getName()).orElse(callerExtension);

            String html = EmailTemplates.unknownCallAlert(
                    agentName, callerExtension,
                    callEvent.getCalledNumber(),
                    OffsetDateTime.now().toString());

            emailService.send(adminEmail, "Alerta: llamada a número sin lead", html);
            logger.info("Alerta número desconocido enviada: ext={} número={}",
                    callerExtension, callEvent.getCalledNumber());
        } catch (Exception e) {
            logger.warn("No se pudo enviar alerta de número desconocido: {}", e.getMessage());
        }
    }
}
