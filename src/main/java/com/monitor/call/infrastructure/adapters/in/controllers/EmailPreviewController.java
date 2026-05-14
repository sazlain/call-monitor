package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.infrastructure.services.EmailService;
import com.monitor.call.infrastructure.services.EmailTemplates;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/email-preview")
@Tag(name = "Email Preview", description = "Previsualización de plantillas de correo (solo ADMIN)")
public class EmailPreviewController {

    private final EmailService emailService;
    private final EmailTemplates emailTemplates;

    public EmailPreviewController(EmailService emailService, EmailTemplates emailTemplates) {
        this.emailService = emailService;
        this.emailTemplates = emailTemplates;
    }

    @PostMapping("/send-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Envía un ejemplo de cada plantilla de correo al destinatario indicado")
    public ResponseEntity<Map<String, String>> sendAll(@RequestParam String to) {

        String now = OffsetDateTime.now().toString();

        emailService.send(to, "Nueva cita agendada",
                emailTemplates.newAppointment(
                        "María González", "Carlos Ramírez", "3001234567",
                        "2026-05-20", "10:30",
                        "Calle 123 # 45-67, Bogotá",
                        2,
                        "Cliente muy interesado, viene con su esposa."));

        emailService.send(to, "Alerta: llamada a número sin lead",
                emailTemplates.unknownCallAlert("Pedro Sánchez", "1005", "3109876543", now));

        emailService.send(to, "Alerta: agentes inactivos (2)",
                emailTemplates.idleAgentsAlert("Pedro Sánchez, Ana López", 30, now));

        emailService.send(to, "Resumen diario — Voxio",
                emailTemplates.dailySummary(
                        "2026-05-14",
                        List.of(
                                new String[]{"María González", "42", "31"},
                                new String[]{"Pedro Sánchez", "38", "25"},
                                new String[]{"Ana López", "29", "18"}
                        ),
                        109L, 74L, 187L));

        emailService.send(to, "Alerta: 2 metas sin cumplir",
                emailTemplates.goalsNotMet(
                        "2026-05-14",
                        List.of(
                                new String[]{"Pedro Sánchez", "CALLS", "DAILY", "23", "40", "57.5"},
                                new String[]{"Ana López", "TALK_TIME", "DAILY", "45", "120", "37.5"}
                        )));

        emailService.send(to, "Callbacks pendientes: 3",
                emailTemplates.pendingCallbacks(
                        "2026-05-14",
                        List.of(
                                new String[]{"Carlos Ramírez", "3001234567", "María González", "2026-05-14"},
                                new String[]{"Laura Herrera", "3157654321", "Pedro Sánchez", "2026-05-15"},
                                new String[]{"Jorge Medina", "3209988776", "Ana López", "2026-05-15"}
                        )));

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "6 correos de ejemplo enviados a " + to,
                "templates", "newAppointment, unknownCallAlert, idleAgentsAlert, dailySummary, goalsNotMet, pendingCallbacks"
        ));
    }
}
