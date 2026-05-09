package com.monitor.call.infrastructure.adapters.out.persistence.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.OffsetDateTime;

@Entity
@Table(name = "agents")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK a users.id — el usuario del sistema asociado a este agente */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * Extension telefonica del agente.
     * Coincide con caller_extension en call_events.
     * Es el vinculo clave entre agente y sus llamadas.
     */
    @Column(name = "extension", nullable = false, unique = true)
    private String extension;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AgentGroupEntity group;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
