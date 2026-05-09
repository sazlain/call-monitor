package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginRequest {
    @NotBlank(message = "El email es requerido")
    @Email(message = "El email no tiene formato valido")
    private String email;

    @NotBlank(message = "La contrasena es requerida")
    private String password;
}
