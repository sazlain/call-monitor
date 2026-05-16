package com.monitor.call.infrastructure.requests;

import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewPaymentRequest {
    private String notes;
}
