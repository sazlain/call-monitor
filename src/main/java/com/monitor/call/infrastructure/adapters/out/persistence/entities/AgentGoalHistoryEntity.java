package com.monitor.call.infrastructure.adapters.out.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "agent_goal_history",
       uniqueConstraints = @UniqueConstraint(columnNames = {"goal_id", "agent_id", "snapshot_date"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentGoalHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "target_value", nullable = false)
    private Double targetValue;

    @Column(name = "actual_value", nullable = false)
    private Double actualValue;

    @Column(name = "achieved", nullable = false)
    private Boolean achieved;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
