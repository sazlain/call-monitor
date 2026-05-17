package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data @Builder
public class PaymentSubmissionResponse {
    private Long id;
    private Long adminId;
    private String adminName;
    private String adminEmail;
    private Long licenseId;
    private PaymentMethodResponse paymentMethod;
    private BigDecimal amount;
    private Integer additionalCallAgents;
    private Integer additionalSalesAgents;
    private String originalFilename;
    private String fileContentType;
    private PaymentStatus status;
    private String adminNotes;
    private String reviewerNotes;
    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    private String reviewedByName;
}
