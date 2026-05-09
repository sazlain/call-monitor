package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateAgentRequest {
    @NotBlank(message = "El nombre es requerido")
    private String name;

    @NotBlank(message = "El email es requerido")
    @Email(message = "Email invalido")
    private String email;

    @NotBlank(message = "La extension es requerida")
    private String extension;

    @NotNull(message = "El grupo es requerido")
    private Long groupId;
}
