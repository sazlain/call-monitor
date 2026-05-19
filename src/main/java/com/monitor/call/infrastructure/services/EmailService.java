package com.monitor.call.infrastructure.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Servicio de envío de correos electrónicos via Elastic Email HTTP API v4.
 * Si el API key no está configurado, los envíos se omiten silenciosamente
 * para no romper el flujo cuando el email no está disponible en el entorno.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final String API_URL = "https://api.elasticemail.com/v4/emails/transactional";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${elasticemail.api.key:}")
    private String apiKey;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${elasticemail.from.name:ZentCall}")
    private String fromName;

    /**
     * Envía un correo HTML via Elastic Email HTTP API.
     *
     * @param to      Destinatario
     * @param subject Asunto
     * @param html    Cuerpo en HTML
     */
    @Async
    public void send(String to, String subject, String html) {
        if (apiKey == null || apiKey.isBlank()) {
            logger.debug("Elastic Email API key no configurado — omitiendo envío a {}: {}", to, subject);
            return;
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            logger.debug("Email remitente no configurado — omitiendo envío a {}: {}", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            logger.debug("Destinatario vacío — omitiendo envío: {}", subject);
            return;
        }

        String escapedHtml = html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        String escapedSubject = subject.replace("\\", "\\\\").replace("\"", "\\\"");

        String body = """
                {
                  "Recipients": { "To": ["%s"] },
                  "Content": {
                    "Body": [{ "ContentType": "HTML", "Content": "%s" }],
                    "Subject": "%s",
                    "From": "%s",
                    "FromName": "%s"
                  },
                  "Options": {
                    "TrackClicks": false,
                    "TrackOpens": false,
                    "IsTransactional": true
                  }
                }
                """.formatted(to, escapedHtml, escapedSubject, fromAddress, fromName);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("X-ElasticEmail-ApiKey", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Email enviado a {}: {}", to, subject);
            } else {
                logger.error("Error al enviar email a {} [{}]: {}", to, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            logger.error("Error al enviar email a {}: {}", to, e.getMessage());
        }
    }

    // ── Plantillas HTML (deprecated — use EmailTemplates instead) ────────────

    /** @deprecated Use {@link EmailTemplates} methods instead. */
    @Deprecated
    public static String row(String label, String value) {
        return "<tr><td style='padding:4px 8px;color:#6b7280;font-size:13px;'>" + label +
               "</td><td style='padding:4px 8px;font-size:13px;font-weight:500;'>" +
               (value != null ? value : "—") + "</td></tr>";
    }

    /** @deprecated Use {@link EmailTemplates} methods instead. */
    @Deprecated
    public static String table(String... rows) {
        StringBuilder sb = new StringBuilder(
                "<table style='border-collapse:collapse;width:100%;margin:12px 0;'>");
        for (String r : rows) sb.append(r);
        sb.append("</table>");
        return sb.toString();
    }

    /** @deprecated Use {@link EmailTemplates} methods instead. */
    @Deprecated
    public static String wrap(String title, String body) {
        return "<div style='font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px;" +
               "background:#f9fafb;border-radius:8px;'>" +
               "<h2 style='color:#1f2937;margin-bottom:16px;'>" + title + "</h2>" +
               body +
               "<hr style='margin-top:24px;border:none;border-top:1px solid #e5e7eb;'/>" +
               "<p style='color:#9ca3af;font-size:12px;margin-top:8px;'>ZentCall — Sistema de gestión de llamadas</p>" +
               "</div>";
    }
}
