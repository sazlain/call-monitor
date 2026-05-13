package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.User;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.UserRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryImplTest {

    @Mock private UserJpaRepository jpaRepo;
    @InjectMocks private UserRepositoryImpl repo;

    private UserEntity buildEntity(Long id, String email) {
        return UserEntity.builder()
                .id(id).name("Test").email(email).password("hash")
                .active(true).roles(Set.of(Role.ADMIN)).build();
    }

    private User buildDomain(String email) {
        return User.builder()
                .id(1L).name("Test").email(email).password("hash")
                .active(true).roles(Set.of(Role.ADMIN)).mustChangePassword(false).build();
    }

    // ─── save ──────────────────────────────────────────────────────────────

    @Test
    void save_delegatesToJpaAndMapsResult() {
        UserEntity entity = buildEntity(1L, "test@test.com");
        when(jpaRepo.save(any())).thenReturn(entity);

        User result = repo.save(buildDomain("test@test.com"));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("test@test.com");
        verify(jpaRepo).save(any());
    }

    // ─── findByEmail ───────────────────────────────────────────────────────

    @Test
    void findByEmail_found_returnsMappedUser() {
        when(jpaRepo.findByEmail("ana@test.com"))
                .thenReturn(Optional.of(buildEntity(1L, "ana@test.com")));

        Optional<User> result = repo.findByEmail("ana@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("ana@test.com");
    }

    @Test
    void findByEmail_notFound_returnsEmpty() {
        when(jpaRepo.findByEmail("no@test.com")).thenReturn(Optional.empty());

        assertThat(repo.findByEmail("no@test.com")).isEmpty();
    }

    // ─── findById ──────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsMappedUser() {
        when(jpaRepo.findById(1L)).thenReturn(Optional.of(buildEntity(1L, "test@test.com")));

        Optional<User> result = repo.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(jpaRepo.findById(99L)).thenReturn(Optional.empty());
        assertThat(repo.findById(99L)).isEmpty();
    }

    // ─── existsByEmail ─────────────────────────────────────────────────────

    @Test
    void existsByEmail_delegatesToJpa() {
        when(jpaRepo.existsByEmail("test@test.com")).thenReturn(true);
        assertThat(repo.existsByEmail("test@test.com")).isTrue();
    }

    // ─── findByRole ────────────────────────────────────────────────────────

    @Test
    void findByRole_returnsListOfUsers() {
        when(jpaRepo.findByRole(Role.ADMIN))
                .thenReturn(List.of(buildEntity(1L, "admin@test.com")));

        List<User> result = repo.findByRole(Role.ADMIN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("admin@test.com");
    }

    // ─── findAll ───────────────────────────────────────────────────────────

    @Test
    void findAll_returnsActiveUsers() {
        when(jpaRepo.findByActiveTrue())
                .thenReturn(List.of(buildEntity(1L, "a@test.com"), buildEntity(2L, "b@test.com")));

        List<User> result = repo.findAll();

        assertThat(result).hasSize(2);
    }

    // ─── deactivate ────────────────────────────────────────────────────────

    @Test
    void deactivate_userExists_setsActiveFalseAndSaves() {
        UserEntity entity = buildEntity(1L, "test@test.com");
        when(jpaRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(jpaRepo.save(any())).thenReturn(entity);

        repo.deactivate(1L);

        assertThat(entity.getActive()).isFalse();
        verify(jpaRepo).save(entity);
    }

    @Test
    void deactivate_userNotFound_doesNothing() {
        when(jpaRepo.findById(99L)).thenReturn(Optional.empty());

        repo.deactivate(99L);

        verify(jpaRepo, never()).save(any());
    }
}
