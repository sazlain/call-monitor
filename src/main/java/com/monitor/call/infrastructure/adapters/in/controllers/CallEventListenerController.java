package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.in.CallEventListenerUseCases;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import com.monitor.call.infrastructure.requests.CallEventRequest;
import com.monitor.call.infrastructure.websocket.CallEventWebSocketHandler;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events/calls")
public class CallEventListenerController {

    private static final Logger logger = LoggerFactory.getLogger(CallEventListenerController.class);

    private final CallEventListenerUseCases callEventListenerUseCases;
    private final CallEventWebSocketHandler webSocketHandler;

    public CallEventListenerController(CallEventListenerUseCases callEventListenerUseCases,
                                       CallEventWebSocketHandler webSocketHandler) {
        this.callEventListenerUseCases = callEventListenerUseCases;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Endpoint unificado para todos los eventos del proveedor de telefonia.
     * Reemplaza a /started — recibe todos los CallStatus en el mismo endpoint.
     * Protegido por WebhookIpFilter (whitelist de IP del proveedor).
     */
    @SecurityRequirements({})
    @PostMapping(value = "event", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<CallEventListenerResponse> onCallEvent(
            @RequestParam Map<String, String> payload) {

        logger.info("Webhook recibido: {}", payload);

        CallEventRequest callEventRequest = CallEventMapper.payloadToRequest(payload);
        CallEvent callEvent = CallEventMapper.requestToDomain(callEventRequest);

        // 1. Guardar en BD
        CallEventListenerResponse response = callEventListenerUseCases.onCallEvent(callEvent);

        // 2. Emitir por WebSocket a agente y grupo en tiempo real
        webSocketHandler.emit(callEvent);

        return ResponseEntity.ok(response);
    }
}
