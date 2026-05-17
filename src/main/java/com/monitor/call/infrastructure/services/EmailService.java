package com.monitor.call.infrastructure.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Servicio de envío de correos electrónicos.
 * Si el username de correo no está configurado, los envíos se omiten silenciosamente
 * para no romper el flujo cuando el email no está disponible en el entorno.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envía un correo HTML.
     *
     * @param to      Destinatario
     * @param subject Asunto
     * @param html    Cuerpo en HTML
     */
    @Async
    public void send(String to, String subject, String html) {
        if (fromAddress == null || fromAddress.isBlank()) {
            logger.debug("Email no configurado — omitiendo envío a {}: {}", to, subject);
            return;
        }
        if (to == null || to.isBlank()) {
            logger.debug("Destinatario vacío — omitiendo envío: {}", subject);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            logger.info("Email enviado a {}: {}", to, subject);
        } catch (MessagingException e) {
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
