package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.in.CallEventListenerUseCases;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import com.monitor.call.infrastructure.mappers.CallEventMapper;
import com.monitor.call.infrastructure.requests.CallEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events/calls")
public class CallEventListenerController {

    private static Logger logger = LoggerFactory.getLogger(CallEventListenerController.class);

    private final CallEventListenerUseCases callEventListenerUseCases;

    public CallEventListenerController(CallEventListenerUseCases callEventListenerUseCases) {
        this.callEventListenerUseCases = callEventListenerUseCases;
    }

    @PostMapping(value = "started", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<CallEventListenerResponse> callStarted(@RequestParam Map<String, String> payload) {
        logger.info("Payload: {}", payload);
        CallEventRequest callEventRequest = CallEventMapper.payloadToRequest(payload);
        CallEvent callEvent = CallEventMapper.requestToDomain(callEventRequest); // Aquí deberías mapear el request a un CallEvent real
        CallEventListenerResponse response = callEventListenerUseCases.onCallStarted(callEvent); // Aquí deberías mapear el request a un CallEvent real
        return ResponseEntity.ok(response);
    }
}
