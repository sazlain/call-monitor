package com.monitor.call.infrastructure.adapters.out.persistence.entities;

import com.monitor.call.domain.enums.GoalKpi;
import com.monitor.call.domain.enums.GoalPeriod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "agent_goals")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGoalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    /** null = aplica a todos los agentes del admin */
    @Column(name = "agent_id")
    private Long agentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kpi_type", nullable = false, length = 50)
    private GoalKpi kpiType;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 20)
    private GoalPeriod period;

    @Column(name = "target_value", nullable = false)
    private Double targetValue;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
