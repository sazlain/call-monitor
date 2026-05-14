package com.monitor.call.infrastructure.adapters.out.persistence.entities;

import com.monitor.call.domain.enums.BillingCycle;
import com.monitor.call.domain.enums.LicenseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "licenses")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LicenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false, unique = true)
    private Long adminId;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "max_agents", nullable = false)
    private Integer maxAgents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LicenseStatus status = LicenseStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    @Builder.Default
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "price_monthly", precision = 12, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "start_date")
    private OffsetDateTime startDate;

    @Column(name = "expiration_date")
    private OffsetDateTime expirationDate;

    @Column(name = "notes", length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
