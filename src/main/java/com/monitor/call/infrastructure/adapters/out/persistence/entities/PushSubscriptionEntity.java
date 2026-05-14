package com.monitor.call.infrastructure.adapters.out.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "push_subscriptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "endpoint"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PushSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "endpoint", nullable = false, length = 1024)
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 512)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 256)
    private String auth;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
