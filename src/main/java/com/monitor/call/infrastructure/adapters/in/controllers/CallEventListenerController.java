package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.in.CallEventListenerUseCases;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import com.monitor.call.infrastructure.requests.CallEventRequest;
import com.monitor.call.infrastructure.websocket.CallEventWebSocketHandler;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Acepta cualquier Content-Type: parsea query params, form-urlencoded body o ambos.
     */
    @SecurityRequirements({})
    @PostMapping(value = "event")
    public ResponseEntity<CallEventListenerResponse> onCallEvent(
            @RequestParam(required = false) Map<String, String> queryParams,
            HttpServletRequest request) {

        // Leer body crudo
        String rawBody = "";
        try {
            rawBody = new BufferedReader(request.getReader())
                    .lines().collect(Collectors.joining("\n"));
        } catch (Exception ignored) {}

        logger.info("Webhook recibido: method={} contentType={} queryParams={} body={}",
                request.getMethod(), request.getContentType(), queryParams, rawBody);

        // Combinar query params + body (form-urlencoded)
        Map<String, String> payload = new HashMap<>();
        if (queryParams != null) payload.putAll(queryParams);
        if (!rawBody.isBlank()) {
            payload.putAll(parseFormBody(rawBody));
        }

        if (payload.isEmpty()) {
            logger.warn("Webhook sin payload — queryParams vacíos y body vacío");
            return ResponseEntity.badRequest().build();
        }

        CallEventRequest callEventRequest;
        CallEvent callEvent;
        try {
            callEventRequest = CallEventMapper.payloadToRequest(payload);
            callEvent = CallEventMapper.requestToDomain(callEventRequest);
        } catch (Exception e) {
            logger.error("Error parseando payload: {} | payload={}", e.getMessage(), payload);
            return ResponseEntity.badRequest().build();
        }

        // Normalizar extensión ANTES de guardar en BD para que las consultas del dashboard funcionen
        // Ejemplo: Net2Phone envía '2007102001' pero el agente está registrado como '2001'
        String rawExtension = callEvent.getCallerExtension();
        String normalizedExtension = webSocketHandler.normalizeExtension(rawExtension);
        if (!normalizedExtension.equals(rawExtension)) {
            logger.info("Extensión normalizada antes de guardar: {} -> {}", rawExtension, normalizedExtension);
            callEvent.setCallerExtension(normalizedExtension);
        }

        logger.info("CallEvent: callId={} status={} flow={} callerExt={} calledNumber={}",
                callEvent.getCallId(), callEvent.getCallStatus(), callEvent.getCallFlow(),
                callEvent.getCallerExtension(), callEvent.getCalledNumber());

        CallEventListenerResponse response;
        try {
            response = callEventListenerUseCases.onCallEvent(callEvent);
            logger.info("Evento guardado en BD: callId={} status={}", response.getCallId(), response.getStatus());
        } catch (Exception e) {
            logger.error("ERROR guardando evento en BD: callId={} status={} — {}",
                    callEvent.getCallId(), callEvent.getCallStatus(), e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }

        // Emitir WS en bloque separado — un fallo aquí no debe afectar el guardado en BD
        try {
            webSocketHandler.emit(callEvent);
        } catch (Exception e) {
            logger.error("ERROR emitiendo WS: callId={} status={} — {}",
                    callEvent.getCallId(), callEvent.getCallStatus(), e.getMessage(), e);
        }

        return ResponseEntity.ok(response);
    }

    private static Map<String, String> parseFormBody(String body) {
        Map<String, String> map = new HashMap<>();
        Arrays.stream(body.split("&")).forEach(pair -> {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                try {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                } catch (Exception ignored) {
                    map.put(kv[0], kv[1]);
                }
            }
        });
        return map;
    }
}
