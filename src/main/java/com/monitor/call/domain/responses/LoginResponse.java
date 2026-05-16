package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.Role;
import lombok.*;
import java.util.Set;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class LoginResponse {
    private String token;
    private String tokenType;
    private Long expiresIn;
    private Long userId;
    private String name;
    private String email;
    private Set<Role> roles;
    private String extension;
    private Boolean mustChangePassword;
    private LicenseStatus licenseStatus;
}
