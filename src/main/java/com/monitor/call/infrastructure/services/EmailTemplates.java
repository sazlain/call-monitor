package com.monitor.call.infrastructure.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Plantillas HTML profesionales para los correos de Voxio.
 * Los archivos HTML se cargan desde src/main/resources/templates/email/
 * y los placeholders {{key}} se sustituyen en tiempo de ejecución.
 */
public final class EmailTemplates {

    private EmailTemplates() {}

    // ── Template loading ──────────────────────────────────────────────────────

    /**
     * Loads an HTML template from the classpath at templates/email/{name}.html.
     * Returns a fallback error string if the resource cannot be loaded.
     */
    private static String loadTemplate(String name) {
        String path = "templates/email/" + name + ".html";
        try (InputStream is = EmailTemplates.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return "<p>Error: template not found: " + path + "</p>";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<p>Error loading email template '" + name + "': " + e.getMessage() + "</p>";
        }
    }

    /**
     * Replaces {{key}} placeholders in the template.
     * Pairs are passed as alternating key, value strings: "key1", "val1", "key2", "val2", ...
     */
    private static String fill(String template, String... keyValuePairs) {
        String result = template;
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            String key = "{{" + keyValuePairs[i] + "}}";
            String value = keyValuePairs[i + 1] != null ? keyValuePairs[i + 1] : "&mdash;";
            result = result.replace(key, value);
        }
        return result;
    }

    // ── Row builders ──────────────────────────────────────────────────────────

    /**
     * Builds a daily-summary table row.
     * row: [agentName, totalCalls, answered]
     * Includes a simple inline percentage bar as a colored span.
     */
    private static String summaryRow(String[] row) {
        String agentName  = row.length > 0 ? row[0] : "";
        String calls      = row.length > 1 ? row[1] : "0";
        String answered   = row.length > 2 ? row[2] : "0";

        double pct = 0;
        try {
            long c = Long.parseLong(calls.trim());
            long a = Long.parseLong(answered.trim());
            if (c > 0) pct = (double) a / c * 100.0;
        } catch (NumberFormatException ignored) {}

        String barColor = pct >= 80 ? "#059669" : (pct >= 50 ? "#D97706" : "#DC2626");
        int barWidth = (int) Math.round(pct);
        String bar = "<span style=\"display:inline-block;width:" + barWidth + "px;max-width:80px;"
                   + "height:8px;background:" + barColor + ";border-radius:4px;\"></span>"
                   + "<span style=\"font-size:11px;color:#6B7280;margin-left:6px;\">"
                   + String.format("%.0f%%", pct) + "</span>";

        return "<tr style=\"border-bottom:1px solid #F3F4F6;\">"
             + "<td style=\"padding:10px 12px;color:#111827;font-weight:600;\">" + agentName + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + calls + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + answered + " " + bar + "</td>"
             + "</tr>";
    }

    /**
     * Builds a goals-not-met table row.
     * row: [agentName, kpi, period, actual, target, progressPercent]
     * Progress badge: red < 50%, yellow 50-79%, green >= 80%.
     */
    private static String goalRow(String[] row) {
        String agentName = row.length > 0 ? row[0] : "";
        String kpi       = row.length > 1 ? row[1] : "";
        String period    = row.length > 2 ? row[2] : "";
        String actual    = row.length > 3 ? row[3] : "";
        String target    = row.length > 4 ? row[4] : "";
        String progress  = row.length > 5 ? row[5] : "0";

        double pct = 0;
        try { pct = Double.parseDouble(progress.trim()); } catch (NumberFormatException ignored) {}

        String badgeColor = pct >= 80 ? "#059669" : (pct >= 50 ? "#D97706" : "#DC2626");
        String badge = "<span style=\"display:inline-block;background:" + badgeColor
                     + ";color:#FFFFFF;font-size:11px;font-weight:700;padding:2px 8px;"
                     + "border-radius:10px;\">" + String.format("%.0f%%", pct) + "</span>";

        return "<tr style=\"border-bottom:1px solid #F3F4F6;\">"
             + "<td style=\"padding:10px 12px;color:#111827;font-weight:600;\">" + agentName + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + kpi + "</td>"
             + "<td style=\"padding:10px 12px;color:#6B7280;\">" + period + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + actual + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + target + "</td>"
             + "<td style=\"padding:10px 12px;\">" + badge + "</td>"
             + "</tr>";
    }

    /**
     * Builds a pending-callbacks table row.
     * row: [contactName, phone, agentName, callbackDate]
     */
    private static String callbackRow(String[] row) {
        String contact  = row.length > 0 ? row[0] : "";
        String phone    = row.length > 1 ? row[1] : "";
        String agent    = row.length > 2 ? row[2] : "";
        String date     = row.length > 3 ? row[3] : "";

        return "<tr style=\"border-bottom:1px solid #F3F4F6;\">"
             + "<td style=\"padding:10px 12px;color:#111827;font-weight:600;\">" + contact + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + phone + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + agent + "</td>"
             + "<td style=\"padding:10px 12px;color:#111827;\">" + date + "</td>"
             + "</tr>";
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Nueva cita agendada.
     * Accent: #059669 (emerald), Icon: 📅
     */
    public static String newAppointment(String agentName, String contactName, String phone,
                                        String appointmentDate, String appointmentTime,
                                        String address, Integer attendees, String notes) {
        String template = loadTemplate("new-appointment");
        return fill(template,
                "agentName",       agentName,
                "contactName",     contactName,
                "contactPhone",    phone,
                "appointmentDate", appointmentDate,
                "appointmentTime", appointmentTime,
                "address",         address,
                "attendees",       attendees != null ? String.valueOf(attendees) : "&mdash;",
                "notes",           notes);
    }

    /**
     * Alerta: llamada a número sin lead.
     * Accent: #DC2626 (red), Icon: 📵
     */
    public static String unknownCallAlert(String agentName, String extension,
                                          String calledNumber, String time) {
        String template = loadTemplate("unknown-call");
        return fill(template,
                "agentName",    agentName,
                "extension",    extension,
                "calledNumber", calledNumber,
                "timestamp",    time);
    }

    /**
     * Alerta: agentes inactivos.
     * Accent: #D97706 (amber), Icon: ⏱
     */
    public static String idleAgentsAlert(String agentNames, int thresholdMinutes, String time) {
        String template = loadTemplate("idle-agents");
        return fill(template,
                "agentNames",        agentNames,
                "thresholdMinutes",  String.valueOf(thresholdMinutes),
                "timestamp",         time);
    }

    /**
     * Resumen diario de llamadas.
     * agentRows: each String[] is [agentName, totalCalls, answered]
     * Accent: #4F46E5 (indigo), Icon: 📊
     */
    public static String dailySummary(String date, List<String[]> agentRows,
                                      long totalCalls, long totalAnswered,
                                      long durationMinutes) {
        double answerRate = totalCalls > 0 ? (double) totalAnswered / totalCalls * 100.0 : 0.0;

        StringBuilder rows = new StringBuilder();
        for (String[] row : agentRows) {
            rows.append(summaryRow(row));
        }

        String template = loadTemplate("daily-summary");
        return fill(template,
                "date",            date,
                "totalCalls",      String.valueOf(totalCalls),
                "totalAnswered",   String.valueOf(totalAnswered),
                "answerRate",      String.format("%.1f", answerRate),
                "durationMinutes", String.valueOf(durationMinutes),
                "tableRows",       rows.toString());
    }

    /**
     * Metas no cumplidas.
     * goalRows: each String[] is [agentName, kpi, period, actual, target, percent]
     * Accent: #7C3AED (purple), Icon: 🎯
     */
    public static String goalsNotMet(String date, List<String[]> goalRows) {
        StringBuilder rows = new StringBuilder();
        for (String[] row : goalRows) {
            rows.append(goalRow(row));
        }

        String template = loadTemplate("goals-not-met");
        return fill(template,
                "date",      date,
                "tableRows", rows.toString());
    }

    /**
     * Callbacks pendientes.
     * callbackRows: each String[] is [contactName, phone, agentName, callbackDate]
     * Accent: #0891B2 (cyan), Icon: 📞
     */
    public static String pendingCallbacks(String date, List<String[]> callbackRows) {
        StringBuilder rows = new StringBuilder();
        for (String[] row : callbackRows) {
            rows.append(callbackRow(row));
        }

        String template = loadTemplate("pending-callbacks");
        return fill(template,
                "date",      date,
                "tableRows", rows.toString());
    }
}
