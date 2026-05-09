package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.responses.LoginResponse;
import com.monitor.call.domain.responses.UserResponse;
import java.util.List;

public interface AuthUseCases {
    LoginResponse login(String email, String password);
    UserResponse registerAdmin(String name, String email, String password);
    void changePassword(Long userId, String currentPassword, String newPassword);
    UserResponse addRole(Long userId, Role role);
    UserResponse removeRole(Long userId, Role role);
    List<UserResponse> listUsers();
    UserResponse getUserById(Long userId);
    void deactivateUser(Long userId);
}
