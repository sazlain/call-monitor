package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ChangePasswordRequest {
    @NotBlank(message = "La contrasena actual es requerida")
    private String currentPassword;

    @NotBlank(message = "La nueva contrasena es requerida")
    @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres")
    private String newPassword;
}
