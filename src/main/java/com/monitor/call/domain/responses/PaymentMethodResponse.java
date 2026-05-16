package com.monitor.call.domain.responses;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data @Builder
public class PaymentMethodResponse {
    private Long id;
    private String name;
    private String details;
    private Boolean active;
    private OffsetDateTime createdAt;
}
