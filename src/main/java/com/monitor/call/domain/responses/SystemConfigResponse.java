package com.monitor.call.domain.responses;

import lombok.*;

import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemConfigResponse {
    private Long id;
    private String configKey;
    private String configValue;
    private String defaultValue;
    private String effectiveValue;
    private Boolean required;
    private String description;
    private String valueType;
    private OffsetDateTime updatedAt;
}
