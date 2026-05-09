package com.monitor.call.infrastructure.adapters.out.persistence.impl;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.mappers.UserMapper;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;

@Component
public class UserRepositoryImpl implements UserRepositoryPort {

    private final UserJpaRepository repo;

    public UserRepositoryImpl(UserJpaRepository repo) { this.repo = repo; }

    @Override public User save(User user) {
        return UserMapper.entityToDomain(repo.save(UserMapper.domainToEntity(user)));
    }
    @Override public Optional<User> findByEmail(String email) {
        return repo.findByEmail(email).map(UserMapper::entityToDomain);
    }
    @Override public Optional<User> findById(Long id) {
        return repo.findById(id).map(UserMapper::entityToDomain);
    }
    @Override public boolean existsByEmail(String email) { return repo.existsByEmail(email); }
    @Override public List<User> findByRole(Role role) {
        return repo.findByRole(role).stream().map(UserMapper::entityToDomain).toList();
    }
    @Override public List<User> findAll() {
        return repo.findByActiveTrue().stream().map(UserMapper::entityToDomain).toList();
    }
    @Override public void deactivate(Long userId) {
        repo.findById(userId).ifPresent(u -> { u.setActive(false); repo.save(u); });
    }
}
