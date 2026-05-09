package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateAgentRequest {
    @NotBlank(message = "El nombre es requerido")
    private String name;

    @NotBlank(message = "La extension es requerida")
    private String extension;
}
