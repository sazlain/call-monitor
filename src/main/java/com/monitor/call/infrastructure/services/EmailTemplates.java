package com.monitor.call.infrastructure.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Plantillas HTML profesionales para los correos de ZentCall.
 * Utiliza Thymeleaf para procesar las plantillas ubicadas en
 * src/main/resources/templates/email/
 */
@Service
public class EmailTemplates {

    private static final Logger logger = LoggerFactory.getLogger(EmailTemplates.class);
    private static final DateTimeFormatter GENERATED_AT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TemplateEngine templateEngine;

    public EmailTemplates(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private String process(String templateName, Consumer<Context> setup) {
        try {
            Context ctx = new Context();
            ctx.setVariable("generatedAt", LocalDateTime.now().format(GENERATED_AT_FMT));
            setup.accept(ctx);
            return templateEngine.process("email/" + templateName, ctx);
        } catch (Exception e) {
            logger.error("Error al procesar plantilla de correo '{}': {}", templateName, e.getMessage(), e);
            return "<p>Error al cargar la plantilla de correo.</p>";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Nueva cita agendada.
     */
    public String newAppointment(String agentName, String contactName, String phone,
                                 String appointmentDate, String appointmentTime,
                                 String address, Integer attendees, String notes) {
        return process("new-appointment", ctx -> {
            ctx.setVariable("agentName", agentName);
            ctx.setVariable("contactName", contactName);
            ctx.setVariable("contactPhone", phone);
            ctx.setVariable("appointmentDate", appointmentDate);
            ctx.setVariable("appointmentTime", appointmentTime);
            ctx.setVariable("address", address);
            ctx.setVariable("attendees", attendees);
            ctx.setVariable("notes", notes);
        });
    }

    /**
     * Alerta: llamada a número sin lead.
     */
    public String unknownCallAlert(String agentName, String extension,
                                   String calledNumber, String time) {
        return process("unknown-call", ctx -> {
            ctx.setVariable("agentName", agentName);
            ctx.setVariable("extension", extension);
            ctx.setVariable("calledNumber", calledNumber);
            ctx.setVariable("timestamp", time);
        });
    }

    /**
     * Alerta: agentes inactivos.
     */
    public String idleAgentsAlert(String agentNames, int thresholdMinutes, String time) {
        return process("idle-agents", ctx -> {
            ctx.setVariable("agentNames", agentNames);
            ctx.setVariable("thresholdMinutes", thresholdMinutes);
            ctx.setVariable("timestamp", time);
        });
    }

    /**
     * Resumen diario de llamadas.
     * agentRows: each String[] is [agentName, totalCalls, answered]
     */
    public String dailySummary(String date, List<String[]> agentRows,
                               long totalCalls, long totalAnswered, long durationMinutes) {
        double rate = totalCalls > 0 ? (double) totalAnswered / totalCalls * 100.0 : 0.0;
        String answerRate = String.format("%.1f%%", rate);

        List<Map<String, String>> rows = new ArrayList<>();
        for (String[] row : agentRows) {
            Map<String, String> map = new HashMap<>();
            map.put("agentName", row.length > 0 ? row[0] : "");
            String calls   = row.length > 1 ? row[1] : "0";
            String answered = row.length > 2 ? row[2] : "0";
            map.put("totalCalls", calls);
            map.put("answered", answered);
            double rowRate = 0;
            try {
                long c = Long.parseLong(calls.trim());
                long a = Long.parseLong(answered.trim());
                if (c > 0) rowRate = (double) a / c * 100.0;
            } catch (NumberFormatException ignored) {}
            map.put("answerRate", String.format("%.1f%%", rowRate));
            rows.add(map);
        }

        return process("daily-summary", ctx -> {
            ctx.setVariable("date", date);
            ctx.setVariable("rows", rows);
            ctx.setVariable("totalCalls", totalCalls);
            ctx.setVariable("totalAnswered", totalAnswered);
            ctx.setVariable("answerRate", answerRate);
            ctx.setVariable("durationMinutes", durationMinutes);
        });
    }

    /**
     * Metas no cumplidas.
     * goalRows: each String[] is [agentName, kpi, period, actual, target, progressPercent]
     */
    public String goalsNotMet(String date, List<String[]> goalRows) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String[] row : goalRows) {
            Map<String, String> map = new HashMap<>();
            map.put("agentName", row.length > 0 ? row[0] : "");
            map.put("kpi",       row.length > 1 ? row[1] : "");
            map.put("period",    row.length > 2 ? row[2] : "");
            map.put("actual",    row.length > 3 ? row[3] : "");
            map.put("target",    row.length > 4 ? row[4] : "");
            String progress = row.length > 5 ? row[5] : "0";
            map.put("progress", progress);
            double pct = 0;
            try { pct = Double.parseDouble(progress.trim()); } catch (NumberFormatException ignored) {}
            String badgeColor = pct >= 80 ? "#059669" : (pct >= 50 ? "#D97706" : "#DC2626");
            map.put("badgeColor", badgeColor);
            rows.add(map);
        }

        return process("goals-not-met", ctx -> {
            ctx.setVariable("date", date);
            ctx.setVariable("rows", rows);
        });
    }

    /**
     * Callbacks pendientes.
     * callbackRows: each String[] is [contactName, phone, agentName, callbackDate]
     */
    public String pendingCallbacks(String date, List<String[]> callbackRows) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String[] row : callbackRows) {
            Map<String, String> map = new HashMap<>();
            map.put("contactName", row.length > 0 ? row[0] : "");
            map.put("phone",       row.length > 1 ? row[1] : "");
            map.put("agentName",   row.length > 2 ? row[2] : "");
            map.put("date",        row.length > 3 ? row[3] : "");
            rows.add(map);
        }

        return process("pending-callbacks", ctx -> {
            ctx.setVariable("date", date);
            ctx.setVariable("rows", rows);
        });
    }
}
