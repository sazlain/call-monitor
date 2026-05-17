package com.monitor.call.infrastructure.adapters.out.persistence.entities;

import com.monitor.call.domain.enums.BillingCycle;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "license_plans")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LicensePlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "default_max_agents", nullable = false)
    private Integer defaultMaxAgents;

    /** Cantidad de call agents incluidos por defecto en este plan */
    @Column(name = "default_max_call_agents")
    @Builder.Default
    private Integer defaultMaxCallAgents = 0;

    /** Cantidad de sales agents incluidos por defecto en este plan */
    @Column(name = "default_max_sales_agents")
    @Builder.Default
    private Integer defaultMaxSalesAgents = 0;

    /** Precio base fijo del plan (puede ser 0) */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Precio mensual por cada slot de Call Agent adquirido */
    @Column(name = "price_per_call_agent", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal pricePerCallAgent = BigDecimal.ZERO;

    /** Precio mensual por cada slot de Sales Agent adquirido */
    @Column(name = "price_per_sales_agent", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal pricePerSalesAgent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
