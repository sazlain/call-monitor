package com.monitor.call.infrastructure.services;

import java.util.List;

/**
 * Plantillas HTML profesionales para los correos de Voxio.
 * Cada método devuelve un String con el HTML completo listo para pasar a EmailService.send().
 * Diseñadas para funcionar en Gmail, Outlook y Apple Mail (inline CSS, table-based layout).
 */
public final class EmailTemplates {

    private EmailTemplates() {}

    // ── Base layout ───────────────────────────────────────────────────────────

    private static String layout(String accentColor, String icon, String title, String bodyContent) {
        return "<!DOCTYPE html>" +
               "<html lang='es'><head><meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width,initial-scale=1.0'>" +
               "<title>" + title + "</title></head>" +
               "<body style='margin:0;padding:0;background:#F3F4F6;font-family:Arial,Helvetica,sans-serif;'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' border='0' " +
               "style='background:#F3F4F6;padding:32px 0;'><tr><td align='center'>" +
               // outer card
               "<table width='600' cellpadding='0' cellspacing='0' border='0' " +
               "style='max-width:600px;width:100%;background:#FFFFFF;border-radius:8px;" +
               "overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,0.10);'>" +
               // header band
               "<tr><td style='background:" + accentColor + ";padding:28px 32px;text-align:left;'>" +
               "<span style='font-size:32px;line-height:1;'>" + icon + "</span>" +
               "<span style='display:inline-block;vertical-align:middle;margin-left:14px;" +
               "font-size:20px;font-weight:700;color:#FFFFFF;letter-spacing:0.3px;'>" +
               title + "</span>" +
               "</td></tr>" +
               // body
               "<tr><td style='padding:28px 32px;'>" +
               bodyContent +
               "</td></tr>" +
               // footer
               "<tr><td style='background:#F9FAFB;padding:16px 32px;border-top:1px solid #E5E7EB;'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' border='0'><tr>" +
               "<td style='font-size:12px;color:#9CA3AF;'>Voxio &mdash; Sistema de gesti&oacute;n</td>" +
               "<td align='right' style='font-size:12px;color:#9CA3AF;'>" +
               "Mensaje autom&aacute;tico de Voxio. No respondas este correo.</td>" +
               "</tr></table>" +
               "</td></tr>" +
               "</table>" + // end card
               "</td></tr></table>" + // end outer
               "</body></html>";
    }

    /** Two-column info table (label | value). */
    private static String infoTable(String[][] rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' cellpadding='0' cellspacing='0' border='0' " +
                  "style='border-collapse:collapse;margin:16px 0;'>");
        for (int i = 0; i < rows.length; i++) {
            String bg = (i % 2 == 0) ? "#F9FAFB" : "#FFFFFF";
            String label = rows[i][0];
            String value = (rows[i][1] != null && !rows[i][1].isEmpty()) ? rows[i][1] : "&mdash;";
            sb.append("<tr style='background:").append(bg).append(";'>")
              .append("<td style='padding:9px 12px;font-size:13px;color:#6B7280;width:40%;'>")
              .append(label).append("</td>")
              .append("<td style='padding:9px 12px;font-size:13px;font-weight:600;color:#111827;'>")
              .append(value).append("</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    /** KPI badge/pill. */
    private static String badge(String text, String color) {
        return "<span style='display:inline-block;background:" + color +
               ";color:#FFFFFF;font-size:12px;font-weight:700;padding:3px 10px;" +
               "border-radius:12px;'>" + text + "</span>";
    }

    /** Paragraph with muted helper text. */
    private static String subtext(String text) {
        return "<p style='font-size:13px;color:#6B7280;margin:4px 0 16px;'>" + text + "</p>";
    }

    /** Section heading inside body. */
    private static String sectionHeading(String text) {
        return "<p style='font-size:15px;font-weight:700;color:#1F2937;margin:20px 0 8px;'>" + text + "</p>";
    }

    /** ASCII-style progress bar (10 blocks total). */
    private static String progressBar(double percent) {
        int filled = (int) Math.round(percent / 10.0);
        filled = Math.max(0, Math.min(10, filled));
        String bar = "█".repeat(filled) + "░".repeat(10 - filled);
        String color = percent >= 100 ? "#059669" : (percent >= 60 ? "#D97706" : "#DC2626");
        return "<span style='font-family:monospace;font-size:14px;color:" + color + ";'>" +
               bar + "</span> <span style='font-size:12px;color:#6B7280;'>" +
               String.format("%.0f%%", percent) + "</span>";
    }

    // ── 1. unknownCallAlert ────────────────────────────────────────────────────

    /**
     * Alerta: llamada a número sin lead.
     * Accent: #DC2626 (red), Icon: 📵
     */
    public static String unknownCallAlert(String agentName, String extension,
                                          String calledNumber, String time) {
        String body =
            subtext("Se detect&oacute; una llamada a un n&uacute;mero sin lead registrado.") +
            infoTable(new String[][]{
                {"Agente",          agentName},
                {"Extensi&oacute;n", extension},
                {"N&uacute;mero marcado", calledNumber},
                {"Hora",            time}
            });
        return layout("#DC2626", "&#128565;", "Llamada a n&uacute;mero sin lead", body);
    }

    // ── 2. idleAgentsAlert ────────────────────────────────────────────────────

    /**
     * Alerta: agentes inactivos.
     * Accent: #D97706 (amber), Icon: ⏱
     */
    public static String idleAgentsAlert(String agentNames, int thresholdMinutes, String time) {
        String body =
            subtext("Los siguientes agentes llevan m&aacute;s de " +
                    "<strong>" + thresholdMinutes + " minutos</strong> sin actividad.") +
            infoTable(new String[][]{
                {"Umbral de inactividad", thresholdMinutes + " min"},
                {"Agentes inactivos",     agentNames},
                {"Hora de verificaci&oacute;n", time}
            });
        return layout("#D97706", "&#9201;", "Agentes inactivos", body);
    }

    // ── 3. dailySummary ───────────────────────────────────────────────────────

    /**
     * Resumen diario de llamadas.
     * agentRows: each String[] is [agentName, calls, answered]
     * Accent: #4F46E5 (indigo), Icon: 📊
     */
    public static String dailySummary(String date, List<String[]> agentRows,
                                      long totalCalls, long totalAnswered,
                                      long durationMinutes) {
        double answerRate = totalCalls > 0
                ? (double) totalAnswered / totalCalls * 100.0 : 0.0;

        // Per-agent table
        StringBuilder agentTable = new StringBuilder();
        agentTable.append("<table width='100%' cellpadding='0' cellspacing='0' border='0' " +
                          "style='border-collapse:collapse;margin:12px 0;'>");
        agentTable.append("<tr style='background:#4F46E5;'>" +
                          "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                          "font-weight:700;'>Agente</th>" +
                          "<th style='padding:9px 12px;text-align:center;font-size:12px;color:#FFFFFF;" +
                          "font-weight:700;'>Llamadas</th>" +
                          "<th style='padding:9px 12px;text-align:center;font-size:12px;color:#FFFFFF;" +
                          "font-weight:700;'>Contestadas</th>" +
                          "</tr>");
        for (int i = 0; i < agentRows.size(); i++) {
            String[] r = agentRows.get(i);
            String bg = (i % 2 == 0) ? "#F9FAFB" : "#FFFFFF";
            agentTable.append("<tr style='background:").append(bg).append(";'>")
                      .append("<td style='padding:8px 12px;font-size:13px;color:#1F2937;'>")
                      .append(r[0]).append("</td>")
                      .append("<td style='padding:8px 12px;font-size:13px;text-align:center;color:#1F2937;'>")
                      .append(r[1]).append("</td>")
                      .append("<td style='padding:8px 12px;font-size:13px;text-align:center;color:#1F2937;'>")
                      .append(r[2]).append("</td>")
                      .append("</tr>");
        }
        agentTable.append("</table>");

        String body =
            subtext("Actividad del d&iacute;a " + date + " para su equipo.") +
            agentTable +
            sectionHeading("Totales del d&iacute;a") +
            infoTable(new String[][]{
                {"Total llamadas",       String.valueOf(totalCalls)},
                {"Llamadas contestadas", String.valueOf(totalAnswered)},
                {"Tasa de respuesta",    String.format("%.1f%%", answerRate)},
                {"Tiempo en llamadas",   durationMinutes + " min"}
            });
        return layout("#4F46E5", "&#128202;", "Resumen del d&iacute;a &mdash; " + date, body);
    }

    // ── 4. goalsNotMet ────────────────────────────────────────────────────────

    /**
     * Metas no cumplidas.
     * goalRows: each String[] is [agentName, kpi, period, actual, target, percent]
     * Accent: #7C3AED (purple), Icon: 🎯
     */
    public static String goalsNotMet(String date, List<String[]> goalRows) {
        StringBuilder table = new StringBuilder();
        table.append("<table width='100%' cellpadding='0' cellspacing='0' border='0' " +
                     "style='border-collapse:collapse;margin:12px 0;'>");
        table.append("<tr style='background:#7C3AED;'>" +
                     "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                     "font-weight:700;'>Agente</th>" +
                     "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                     "font-weight:700;'>KPI / Per&iacute;odo</th>" +
                     "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                     "font-weight:700;'>Avance</th>" +
                     "</tr>");
        for (int i = 0; i < goalRows.size(); i++) {
            String[] r = goalRows.get(i);
            // r: [agentName, kpi, period, actual, target, percent]
            String bg = (i % 2 == 0) ? "#F9FAFB" : "#FFFFFF";
            double pct = 0;
            try { pct = Double.parseDouble(r[5]); } catch (Exception ignored) {}
            table.append("<tr style='background:").append(bg).append(";'>")
                 .append("<td style='padding:8px 12px;font-size:13px;color:#1F2937;font-weight:600;'>")
                 .append(r[0]).append("</td>")
                 .append("<td style='padding:8px 12px;font-size:13px;color:#374151;'>")
                 .append(r[1]).append("<br><span style='font-size:11px;color:#9CA3AF;'>").append(r[2]).append("</span></td>")
                 .append("<td style='padding:8px 12px;font-size:13px;'>")
                 .append(r[3]).append(" / ").append(r[4]).append("<br>")
                 .append(progressBar(pct))
                 .append("</td></tr>");
        }
        table.append("</table>");

        String body =
            subtext("Los siguientes agentes no alcanzaron su meta del d&iacute;a " + date + ".") +
            table;
        return layout("#7C3AED", "&#127919;", "Metas no cumplidas &mdash; " + date, body);
    }

    // ── 5. pendingCallbacks ───────────────────────────────────────────────────

    /**
     * Callbacks pendientes.
     * callbackRows: each String[] is [contactName, phone, agentName, callbackDate]
     * Accent: #0891B2 (cyan), Icon: 📞
     */
    public static String pendingCallbacks(String date, List<String[]> callbackRows) {
        StringBuilder table = new StringBuilder();
        table.append("<table width='100%' cellpadding='0' cellspacing='0' border='0' " +
                     "style='border-collapse:collapse;margin:12px 0;'>");
        table.append("<tr style='background:#0891B2;'>" +
                     "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                     "font-weight:700;'>Contacto</th>" +
                     "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                     "font-weight:700;'>Tel&eacute;fono</th>" +
                     "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                     "font-weight:700;'>Agente</th>" +
                     "<th style='padding:9px 12px;text-align:left;font-size:12px;color:#FFFFFF;" +
                     "font-weight:700;'>Fecha callback</th>" +
                     "</tr>");
        for (int i = 0; i < callbackRows.size(); i++) {
            String[] r = callbackRows.get(i);
            String bg = (i % 2 == 0) ? "#F9FAFB" : "#FFFFFF";
            table.append("<tr style='background:").append(bg).append(";'>")
                 .append("<td style='padding:8px 12px;font-size:13px;color:#1F2937;font-weight:600;'>")
                 .append(r[0]).append("</td>")
                 .append("<td style='padding:8px 12px;font-size:13px;color:#374151;'>").append(r[1]).append("</td>")
                 .append("<td style='padding:8px 12px;font-size:13px;color:#374151;'>").append(r[2]).append("</td>")
                 .append("<td style='padding:8px 12px;font-size:13px;color:#374151;'>").append(r[3]).append("</td>")
                 .append("</tr>");
        }
        table.append("</table>");

        String body =
            subtext("Hay " + badge(String.valueOf(callbackRows.size()), "#0891B2") +
                    " callbacks pendientes para hoy, " + date + ".") +
            table;
        return layout("#0891B2", "&#128222;", "Callbacks pendientes &mdash; " + date, body);
    }

    // ── 6. newAppointment ─────────────────────────────────────────────────────

    /**
     * Nueva cita agendada.
     * Accent: #059669 (emerald), Icon: 📅
     */
    public static String newAppointment(String agentName, String contactName, String phone,
                                        String appointmentDate, String appointmentTime,
                                        String address, Integer attendees, String notes) {
        String body =
            subtext("Se ha agendado una nueva cita a trav&eacute;s de Voxio.") +
            infoTable(new String[][]{
                {"Agente",          agentName},
                {"Contacto",        contactName},
                {"Tel&eacute;fono", phone},
                {"Fecha",           appointmentDate},
                {"Hora",            appointmentTime},
                {"Lugar",           address},
                {"Asistentes",      attendees != null ? String.valueOf(attendees) : null},
                {"Notas",           notes}
            });
        return layout("#059669", "&#128197;", "Nueva cita agendada", body);
    }
}
