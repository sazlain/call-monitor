package com.monitor.call.infrastructure.requests;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AssignLeadRequest {
    @NotNull(message = "El id del agente asignado es requerido")
    private Long assignedAgentId;
}
