package com.monitor.call.infrastructure.adapters.out.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "system_configs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "config_key"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;

    @Column(name = "required", nullable = false)
    @Builder.Default
    private Boolean required = false;

    @Column(name = "description", length = 255)
    private String description;

    /** STRING | INTEGER | BOOLEAN | JSON */
    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "STRING";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
