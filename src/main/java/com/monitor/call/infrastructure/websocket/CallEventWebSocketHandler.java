package com.monitor.call.infrastructure.websocket;

import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.out.LeadRepositoryPort;
import com.monitor.call.domain.responses.CallEventWebSocketMessage;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
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

    public CallEventWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                                     AgentJpaRepository agentJpaRepository,
                                     LeadRepositoryPort leadRepo) {
        this.messagingTemplate = messagingTemplate;
        this.agentJpaRepository = agentJpaRepository;
        this.leadRepo = leadRepo;
    }

    /**
     * Emite el evento al canal del agente y al canal del grupo al que pertenece.
     * Se llama desde CallEventListenerImpl despues de guardar el evento en BD.
     */
    public void emit(CallEvent callEvent) {
        CallEventWebSocketMessage message = buildMessage(callEvent);
        String rawExtension = callEvent.getCallerExtension();

        // Buscar agente por extensión completa primero
        // Si no encuentra, buscar por los últimos N dígitos
        String extension = rawExtension;
        if (agentJpaRepository.findByExtension(rawExtension).isEmpty()) {
            // Intentar sufijos de 4, 5, 6 dígitos hasta encontrar el agente
            for (int len = 4; len <= rawExtension.length(); len++) {
                String suffix = rawExtension.substring(rawExtension.length() - len);
                if (agentJpaRepository.findByExtension(suffix).isPresent()) {
                    extension = suffix;
                    logger.info("Extensión normalizada: {} -> {}", rawExtension, extension);
                    break;
                }
            }
        }

        // Emitir al canal del agente
        String agentChannel = "/topic/calls/agent/" + extension;
        messagingTemplate.convertAndSend(agentChannel, message);
        logger.info("WS emitido a {}: callId={} status={}", agentChannel,
                callEvent.getCallId(), callEvent.getCallStatus());

        // Emitir al grupo
        agentJpaRepository.findByExtension(extension).ifPresent(agent -> {
            if (agent.getGroup() != null) {
                String groupChannel = "/topic/calls/group/" + agent.getGroup().getId();
                messagingTemplate.convertAndSend(groupChannel, message);
            }
        });
    }

    private CallEventWebSocketMessage buildMessage(CallEvent callEvent) {

        // Determinar el número del contacto según el flujo de la llamada
        String phone;
        if ("out".equalsIgnoreCase(String.valueOf(callEvent.getCallFlow()))) {
            // Saliente → el contacto es el número destino (calledNumber)
            phone = callEvent.getCalledNumber();
        } else {
            // Entrante → el contacto es quien llama (callerIdNum)
            phone = callEvent.getCallerIdNum();
        }

// Normalizar — quitar prefijo Colombia
        String normalizedPhone = phone != null
                ? phone.replaceAll("^(\\+?57)", "")
                : null;

        Lead lead = null;
        if (normalizedPhone != null && !normalizedPhone.isBlank()) {
            lead = leadRepo.findActiveByPhone(normalizedPhone)
                    .orElse(leadRepo.findActiveByPhone(phone).orElse(null));
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
                .frontendAction(resolveFrontendAction(callEvent.getCallStatus(), lead))
                .leadId(lead != null ? lead.getId() : null)
                .leadContactName(lead != null ? lead.getContactName() : null)
                .leadContactPhone(lead != null ? lead.getContactPhone() : null)
                .leadNotes(lead != null ? lead.getNotes() : null)
                .leadStatus(lead != null ? lead.getStatus().name() : null)
                .build();
    }

    /**
     * Traduce el CallStatus a una accion clara para el frontend.
     * El frontend no necesita saber logica de negocio — solo ejecuta la accion.
     */
    private String resolveFrontendAction(CallStatus status, Lead lead) {
        return switch (status) {
            case CALLING -> "OPEN_CONTACT_FORM";
            case ANSWER -> "START_CALL_TIMER";
            case HANGUP -> lead != null ? "OPEN_TYPIFICATION_FORM" : "ASK_CREATE_LEAD";
            case BUSY, NOANSWER, CANCEL, CONGESTION, CHANUNAVAIL -> "REGISTER_FAILED_ATTEMPT";
        };
    }
}
