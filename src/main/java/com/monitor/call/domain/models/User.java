package com.monitor.call.domain.models;

import com.monitor.call.domain.enums.Role;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.Set;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    private Long id;
    private String name;
    private String email;
    private String password;
    private Boolean active;
    private Set<Role> roles;
    private Boolean mustChangePassword;
    private Long adminId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
