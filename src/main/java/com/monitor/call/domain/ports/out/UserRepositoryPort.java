package com.monitor.call.domain.ports.out;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.User;
import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findByEmail(String email);
    Optional<User> findById(Long id);
    List<User> findAllById(List<Long> ids);
    boolean existsByEmail(String email);
    List<User> findByRole(Role role);
    List<User> findAll();
    void deactivate(Long userId);
}
