package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.LeadStatus;
import java.util.Arrays;
import com.monitor.call.domain.ports.in.LeadUseCases;
import com.monitor.call.domain.responses.BulkLeadResponse;
import com.monitor.call.domain.responses.LeadResponse;
import com.monitor.call.infrastructure.requests.AssignLeadRequest;
import com.monitor.call.infrastructure.requests.CreateLeadRequest;
import com.monitor.call.infrastructure.security.JwtUtil;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/leads")
@Tag(name = "Leads", description = "Gestion de leads")
public class LeadController {

    private final LeadUseCases leadUseCases;
    private final JwtUtil jwtUtil;

    public LeadController(LeadUseCases leadUseCases, JwtUtil jwtUtil) {
        this.leadUseCases = leadUseCases;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Crear un lead individual")
    public ResponseEntity<LeadResponse> create(
            @Valid @RequestBody CreateLeadRequest request,
            @RequestHeader("Authorization") String auth) {

        Long ownerId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.status(HttpStatus.CREATED).body(leadUseCases.createLead(request, ownerId));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Carga masiva de leads desde CSV. Columnas: contact_name,contact_phone,lead_source,lead_date,notes,status")
    public ResponseEntity<BulkLeadResponse> bulkCreate(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestHeader("Authorization") String auth) {

        Long ownerId = jwtUtil.extractUserId(auth.substring(7));
        List<CreateLeadRequest> leads = parseCsv(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(leadUseCases.createBulkLeads(leads, ownerId, assignedAgentId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Listar mis leads. Filtros opcionales: status, from, to")
    public ResponseEntity<List<LeadResponse>> list(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader("Authorization") String auth) {

        Long ownerId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(leadUseCases.listLeadsByOwner(ownerId, status, from, to));
    }

    @GetMapping("/assigned")
    public ResponseEntity<List<LeadResponse>> queue(
            @RequestHeader("Authorization") String auth) {
        Long userId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(leadUseCases.listAssignedLeads(userId));
    }

    @GetMapping("/callbacks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT', 'CALL_AGENT')")
    @Operation(summary = "Callbacks pendientes para hoy o vencidos")
    public ResponseEntity<List<LeadResponse>> listCallbacks(
            @RequestHeader("Authorization") String auth) {

        Long userId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(leadUseCases.listPendingCallbacks(userId));
    }

    @GetMapping("/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT', 'CALL_AGENT')")
    @Operation(summary = "Obtener detalle de un lead")
    public ResponseEntity<LeadResponse> get(@PathVariable Long leadId) {
        return ResponseEntity.ok(leadUseCases.getLead(leadId));
    }

    @PutMapping("/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Actualizar datos de un lead")
    public ResponseEntity<LeadResponse> update(
            @PathVariable Long leadId,
            @Valid @RequestBody CreateLeadRequest request,
            @RequestHeader("Authorization") String auth) {

        Long requesterId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(leadUseCases.updateLead(leadId, request, requesterId));
    }

    @PutMapping("/{leadId}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Asignar lead a un call_agent")
    public ResponseEntity<LeadResponse> assign(
            @PathVariable Long leadId,
            @Valid @RequestBody AssignLeadRequest request,
            @RequestHeader("Authorization") String auth) {

        Long requesterId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(leadUseCases.assignLead(leadId, request.getAssignedAgentId(), requesterId));
    }

    @DeleteMapping("/{leadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_AGENT')")
    @Operation(summary = "Descartar un lead")
    public ResponseEntity<Void> discard(
            @PathVariable Long leadId,
            @RequestHeader("Authorization") String auth) {

        Long requesterId = jwtUtil.extractUserId(auth.substring(7));
        leadUseCases.discardLead(leadId, requesterId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Parsea el CSV con columnas (el orden importa, el header define la posición):
     *   contact_name*, contact_phone*, lead_source, lead_date, notes, status
     *
     * La columna status es opcional; valores válidos: NEW, PENDING, CALLED, CONTACTED,
     * INTERESTED, CONVERTED, CALLBACK, DISCARDED, APPOINTMENT.
     * Si está vacía o ausente se aplica el fallback (PENDING con agente / NEW sin agente).
     */
    private List<CreateLeadRequest> parseCsv(MultipartFile file) {
        List<CreateLeadRequest> leads = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return leads;

            // Detectar posición de cada columna por nombre de header (case-insensitive)
            String[] headers = Arrays.stream(rows.get(0))
                    .map(h -> h.trim().toLowerCase().replace(" ", "_"))
                    .toArray(String[]::new);
            int iName   = indexOf(headers, "contact_name");
            int iPhone  = indexOf(headers, "contact_phone");
            int iSource = indexOf(headers, "lead_source");
            int iDate   = indexOf(headers, "lead_date");
            int iNotes  = indexOf(headers, "notes");
            int iStatus = indexOf(headers, "status");

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length == 0 || (row.length == 1 && row[0].isBlank())) continue;

                LocalDate date = null;
                try {
                    String rawDate = get(row, iDate);
                    if (!rawDate.isBlank()) date = LocalDate.parse(rawDate);
                } catch (Exception ignored) { }

                LeadStatus status = null;
                try {
                    String rawStatus = get(row, iStatus).toUpperCase();
                    if (!rawStatus.isBlank()) status = LeadStatus.valueOf(rawStatus);
                } catch (IllegalArgumentException e) {
                    // valor de status inválido → se usará el fallback en el use case
                }

                leads.add(CreateLeadRequest.builder()
                        .contactName(get(row, iName))
                        .contactPhone(get(row, iPhone))
                        .leadSource(get(row, iSource))
                        .leadDate(date != null ? date : LocalDate.now())
                        .notes(get(row, iNotes))
                        .status(status)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al parsear CSV: " + e.getMessage());
        }
        return leads;
    }

    /** Devuelve el índice de una columna por nombre, -1 si no existe. */
    private int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) if (headers[i].equals(name)) return i;
        return -1;
    }

    /** Lee una celda de la fila de forma segura; retorna "" si la columna no existe. */
    private String get(String[] row, int index) {
        return (index >= 0 && index < row.length) ? row[index].trim() : "";
    }
}
