package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.ports.in.ReportUseCases;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reportes", description = "Exportacion de datos en CSV — solo ADMIN")
public class ReportController {

    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final MediaType CSV = new MediaType("text", "csv");

    private final ReportUseCases reportUseCases;
    private final JwtUtil jwtUtil;

    public ReportController(ReportUseCases reportUseCases, JwtUtil jwtUtil) {
        this.reportUseCases = reportUseCases;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Reporte detallado de llamadas de un agente.
     * Columnas: fecha, callId, numero, duracion, estado, tipificacion, resultado, notas.
     */
    @GetMapping("/agent/{extension}/calls")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar CSV de llamadas detalladas de un agente")
    public ResponseEntity<byte[]> agentCallReport(
            @PathVariable String extension,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {

        OffsetDateTime[] range = resolveRange(from, to);
        byte[] csv = reportUseCases.generateAgentCallReport(extension, range[0], range[1]);
        String filename = "llamadas_" + extension + "_" + range[0].format(FILE_FMT) + "_" + range[1].format(FILE_FMT) + ".csv";
        return csvResponse(csv, filename);
    }

    /**
     * Reporte comparativo de todos los agentes de un grupo.
     * Columnas: agente, total, contestadas, tasa_contacto, duracion, ventas, conversion.
     */
    @GetMapping("/group")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar CSV comparativo de agentes por grupo")
    public ResponseEntity<byte[]> groupReport(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestHeader("Authorization") String auth) {

        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        OffsetDateTime[] range = resolveRange(from, to);
        byte[] csv = reportUseCases.generateGroupReport(adminId, groupId, range[0], range[1]);
        String filename = "reporte_grupo_" + range[0].format(FILE_FMT) + "_" + range[1].format(FILE_FMT) + ".csv";
        return csvResponse(csv, filename);
    }

    /**
     * Reporte de leads del agente de ventas autenticado.
     * Columnas: fecha, contacto, telefono, origen, estado, agente, tipificacion, resultado.
     */
    @GetMapping("/leads")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Exportar CSV de leads con estado y tipificacion")
    public ResponseEntity<byte[]> leadReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestHeader("Authorization") String auth) {

        Long ownerId = jwtUtil.extractUserId(auth.substring(7));
        OffsetDateTime[] range = resolveRange(from, to);
        byte[] csv = reportUseCases.generateLeadReport(ownerId, range[0], range[1]);
        String filename = "leads_" + range[0].format(FILE_FMT) + "_" + range[1].format(FILE_FMT) + ".csv";
        return csvResponse(csv, filename);
    }

    /**
     * Reporte de callbacks pendientes y vencidos.
     * Columnas: contacto, telefono, origen, agente, fecha_callback, vencido, dias.
     */
    @GetMapping("/callbacks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT', 'CALL_AGENT')")
    @Operation(summary = "Exportar CSV de callbacks pendientes y vencidos")
    public ResponseEntity<byte[]> callbackReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestHeader("Authorization") String auth) {

        Long userId = jwtUtil.extractUserId(auth.substring(7));
        OffsetDateTime[] range = resolveRange(from, to);
        byte[] csv = reportUseCases.generateCallbackReport(userId, range[0], range[1]);
        String filename = "callbacks_" + range[0].format(FILE_FMT) + "_" + range[1].format(FILE_FMT) + ".csv";
        return csvResponse(csv, filename);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> csvResponse(byte[] csv, String filename) {
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(csv);
    }

    private OffsetDateTime[] resolveRange(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime resolvedFrom = from != null ? from : now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime resolvedTo   = to   != null ? to   : now;
        return new OffsetDateTime[]{resolvedFrom, resolvedTo};
    }
}
