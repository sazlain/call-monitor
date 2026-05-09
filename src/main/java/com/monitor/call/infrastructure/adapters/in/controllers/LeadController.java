package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.LeadStatus;
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
    @Operation(summary = "Carga masiva de leads desde CSV. Columnas: contact_name,contact_phone,lead_source,lead_date,notes")
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
     * Parsea el CSV con columnas: contact_name,contact_phone,lead_source,lead_date,notes
     * La primera fila se asume como header y se omite.
     */
    private List<CreateLeadRequest> parseCsv(MultipartFile file) {
        List<CreateLeadRequest> leads = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = reader.readAll();
            // Saltar header
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < 2) continue;
                LocalDate date = null;
                try {
                    if (row.length > 3 && !row[3].isBlank())
                        date = LocalDate.parse(row[3].trim());
                } catch (Exception e) {
                    date = LocalDate.now();
                }
                leads.add(CreateLeadRequest.builder()
                        .contactName(row[0].trim())
                        .contactPhone(row[1].trim())
                        .leadSource(row.length > 2 ? row[2].trim() : null)
                        .leadDate(date != null ? date : LocalDate.now())
                        .notes(row.length > 4 ? row[4].trim() : null)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al parsear CSV: " + e.getMessage());
        }
        return leads;
    }
}
