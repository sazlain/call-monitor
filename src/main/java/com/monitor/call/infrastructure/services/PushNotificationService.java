package com.monitor.call.infrastructure.services;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.PushSubscriptionEntity;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;

@Service
public class PushNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    @Value("${vapid.public.key}")
    private String vapidPublicKey;

    @Value("${vapid.private.key}")
    private String vapidPrivateKey;

    @Value("${vapid.subject}")
    private String vapidSubject;

    private PushService pushService;

    @PostConstruct
    public void init() {
        try {
            Security.addProvider(new BouncyCastleProvider());
            pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            logger.info("PushService inicializado correctamente");
        } catch (Exception e) {
            logger.error("Error al inicializar PushService: {}", e.getMessage(), e);
        }
    }

    public void send(PushSubscriptionEntity sub, String title, String body, String icon, String url) {
        if (pushService == null) return;
        try {
            Subscription subscription = new Subscription(
                    sub.getEndpoint(),
                    new Subscription.Keys(sub.getP256dh(), sub.getAuth())
            );
            String payload = String.format(
                    "{\"title\":\"%s\",\"body\":\"%s\",\"icon\":\"%s\",\"url\":\"%s\"}",
                    esc(title), esc(body),
                    icon != null ? icon : "/pwa-192x192.png",
                    url != null ? url : "/"
            );
            pushService.send(new Notification(subscription, payload));
            logger.debug("Push enviado a userId={}", sub.getUserId());
        } catch (Exception e) {
            logger.warn("Error al enviar push a userId={}: {}", sub.getUserId(), e.getMessage());
        }
    }

    public void sendToAll(List<PushSubscriptionEntity> subs, String title, String body,
                          String icon, String url) {
        for (PushSubscriptionEntity sub : subs) {
            send(sub, title, body, icon, url);
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
