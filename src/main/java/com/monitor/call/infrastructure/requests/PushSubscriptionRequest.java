package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushSubscriptionRequest {

    @NotBlank(message = "El endpoint es requerido")
    private String endpoint;

    @NotBlank(message = "La clave p256dh es requerida")
    private String p256dh;

    @NotBlank(message = "La clave auth es requerida")
    private String auth;
}
