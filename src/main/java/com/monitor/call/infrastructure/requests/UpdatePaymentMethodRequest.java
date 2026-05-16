package com.monitor.call.infrastructure.requests;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdatePaymentMethodRequest {
    private String name;
    private String details;
    private Boolean active;
}
