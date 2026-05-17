package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.Role;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.Set;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPresenceInfo {
    private String sessionId;
    private Long userId;
    private String name;
    private String email;
    private Set<Role> roles;
    private OffsetDateTime connectedAt;
}
