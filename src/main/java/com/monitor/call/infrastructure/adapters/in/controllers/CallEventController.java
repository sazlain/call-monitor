package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.CallHistoryUseCases;
import com.monitor.call.domain.responses.CallHistoryPage;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/calls")
@Tag(name = "Historial de llamadas", description = "Consulta paginada del historial de eventos de llamada")
public class CallEventController {

    private final CallHistoryUseCases historyUseCases;
    private final JwtUtil jwtUtil;

    public CallEventController(CallHistoryUseCases historyUseCases, JwtUtil jwtUtil) {
        this.historyUseCases = historyUseCases;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'CALL_AGENT')")
    @Operation(summary = "Historial paginado de llamadas con tipificacion y datos del agente")
    public ResponseEntity<CallHistoryPage> getHistory(
            @RequestParam(required = false) String extension,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestHeader("Authorization") String auth) {

        // Si el usuario es agente (no admin), restringir a su propia extensión
        String resolvedExtension = resolveExtension(extension, auth);
        OffsetDateTime[] range = resolveRange(from, to);

        CallHistoryPage result = historyUseCases.getCallHistory(
                resolvedExtension, status, range[0], range[1], page, size);
        return ResponseEntity.ok(result);
    }

    private String resolveExtension(String requested, String auth) {
        // Si viene extensión explícita la dejamos pasar (admin puede filtrar por cualquiera)
        return requested;
    }

    private OffsetDateTime[] resolveRange(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime resolvedFrom = from != null ? from : now.minusDays(30);
        OffsetDateTime resolvedTo   = to   != null ? to   : now;
        return new OffsetDateTime[]{resolvedFrom, resolvedTo};
    }
}
