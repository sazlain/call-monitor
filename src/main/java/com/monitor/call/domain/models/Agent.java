package com.monitor.call.domain.models;

import lombok.*;
import java.time.OffsetDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Agent {
    private Long id;
    private Long userId;
    private String extension;
    private String userName;   // nombre del usuario asociado (join desde users)
    private String userEmail;  // email del usuario asociado
    private Boolean active;
    private Long adminId;    // id del admin dueño del grupo al que pertenece este agente
    private Long groupId;
    private String groupName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
