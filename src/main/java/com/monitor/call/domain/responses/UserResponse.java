package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.Role;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.Set;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private Boolean active;
    private Set<Role> roles;
    private Boolean mustChangePassword;
    private OffsetDateTime createdAt;
}
