package com.monitor.call.domain.models;

import lombok.*;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemConfig {
    private Long id;
    private Long adminId;
    private String configKey;
    private String configValue;
    private String defaultValue;
    private Boolean required;
    private String description;
    private String valueType;   // STRING | INTEGER | BOOLEAN | JSON
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    /** Retorna el valor efectivo: configValue si no es null, sino defaultValue. */
    public String effectiveValue() {
        return configValue != null ? configValue : defaultValue;
    }
}
