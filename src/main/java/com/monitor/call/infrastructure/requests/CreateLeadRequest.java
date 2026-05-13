package com.monitor.call.infrastructure.requests;

import com.monitor.call.domain.enums.LeadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateLeadRequest {
    @NotBlank(message = "El nombre del contacto es requerido")
    private String contactName;

    @NotBlank(message = "El telefono del contacto es requerido")
    private String contactPhone;

    private String leadSource;
    private String notes;

    @NotNull(message = "La fecha del lead es requerida")
    private LocalDate leadDate;

    private Long assignedAgentId;

    /**
     * Estado inicial del lead. Si no se especifica:
     * - Creación individual → PENDING
     * - Carga masiva sin agente asignado → NEW; con agente → PENDING
     */
    private LeadStatus status;
}
