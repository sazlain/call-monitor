package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateGroupRequest {
    @NotBlank(message = "El nombre del grupo es requerido")
    private String name;

    private String description;
}
