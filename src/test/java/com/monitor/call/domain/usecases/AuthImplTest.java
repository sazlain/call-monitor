package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.LoginResponse;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.exceptions.SupportMessages;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthImplTest {

    @Mock private UserRepositoryPort userRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private SupportMessages supportMessages;
    @Mock private AgentJpaRepository agentJpaRepository;

    @InjectMocks
    private AuthImpl auth;

    // ─── helpers ────────────────────────────────────────────────────────────────

    private User activeUser() {
        return User.builder()
                .id(1L).name("Test User").email("test@test.com")
                .password("hashedPass").active(true)
                .roles(new HashSet<>(Set.of(Role.CALL_AGENT)))
                .mustChangePassword(false).build();
    }

    private void stubSuccessfulLogin(User user, String token) {
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(agentJpaRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(jwtUtil.generateToken(eq(user.getId()), eq(user.getEmail()), any(), isNull())).thenReturn(token);
        when(jwtUtil.getExpirationMinutes()).thenReturn(60L);
    }

    // ─── login ──────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsLoginResponse() {
        User user = activeUser();
        stubSuccessfulLogin(user, "jwt-token");

        LoginResponse resp = auth.login("test@test.com", "pass123");

        assertThat(resp.getToken()).isEqualTo("jwt-token");
        assertThat(resp.getUserId()).isEqualTo(1L);
        assertThat(resp.getEmail()).isEqualTo("test@test.com");
        assertThat(resp.getName()).isEqualTo("Test User");
    }

    @Test
    void login_agentUser_includesExtensionInResponse() {
        User user = activeUser();
        AgentEntity agent = AgentEntity.builder().id(10L).userId(1L).extension("1001").active(true).build();
        when(userRepo.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "hashedPass")).thenReturn(true);
        when(agentJpaRepository.findByUserId(1L)).thenReturn(Optional.of(agent));
        when(jwtUtil.generateToken(1L, "test@test.com", user.getRoles(), "1001")).thenReturn("jwt-ext");
        when(jwtUtil.getExpirationMinutes()).thenReturn(60L);

        LoginResponse resp = auth.login("test@test.com", "pass123");

        assertThat(resp.getExtension()).isEqualTo("1001");
    }

    @Test
    void login_userNotFound_throwsRuntimeException() {
        when(userRepo.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login("no@exist.com", "x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Credenciales");
    }

    @Test
    void login_inactiveUser_throwsRuntimeException() {
        User user = User.builder().id(2L).email("inactive@test.com")
                .password("hash").active(false)
                .roles(Set.of(Role.CALL_AGENT)).mustChangePassword(false).build();
        when(userRepo.findByEmail("inactive@test.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.login("inactive@test.com", "pass"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void login_wrongPassword_throwsRuntimeException() {
        User user = activeUser();
        when(userRepo.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "hashedPass")).thenReturn(false);

        assertThatThrownBy(() -> auth.login("test@test.com", "wrongPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Credenciales");
    }

    // ─── registerAdmin ───────────────────────────────────────────────────────────

    @Test
    void registerAdmin_newEmail_savesAndReturnsUserResponse() {
        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("encodedPass");
        User saved = User.builder().id(2L).name("New Admin").email("new@test.com")
                .password("encodedPass").active(true)
                .roles(Set.of(Role.ADMIN)).mustChangePassword(false).build();
        when(userRepo.save(any())).thenReturn(saved);

        UserResponse resp = auth.registerAdmin("New Admin", "new@test.com", "pass");

        assertThat(resp.getEmail()).isEqualTo("new@test.com");
        assertThat(resp.getId()).isEqualTo(2L);
        verify(userRepo).save(any());
    }

    @Test
    void registerAdmin_duplicateEmail_throwsRuntimeException() {
        when(userRepo.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> auth.registerAdmin("Name", "existing@test.com", "pass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("email");
    }

    @Test
    void registerAdmin_encodesPassword() {
        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPass")).thenReturn("BCRYPT_HASH");
        User saved = User.builder().id(3L).name("Admin").email("new@test.com")
                .password("BCRYPT_HASH").active(true)
                .roles(Set.of(Role.ADMIN)).mustChangePassword(false).build();
        when(userRepo.save(any())).thenReturn(saved);

        auth.registerAdmin("Admin", "new@test.com", "plainPass");

        verify(passwordEncoder).encode("plainPass");
        verify(userRepo).save(argThat(u -> u.getPassword().equals("BCRYPT_HASH")));
    }

    // ─── changePassword ───────────────────────────────────────────────────────────

    @Test
    void changePassword_correctCurrentPassword_updatesAndSaves() {
        User user = activeUser();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("currentPass", "hashedPass")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("newHash");
        when(userRepo.save(any())).thenReturn(user);

        auth.changePassword(1L, "currentPass", "newPass");

        verify(userRepo).save(argThat(u -> !u.getMustChangePassword()));
        verify(passwordEncoder).encode("newPass");
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsRuntimeException() {
        User user = activeUser();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashedPass")).thenReturn(false);

        assertThatThrownBy(() -> auth.changePassword(1L, "wrong", "new"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("contrasena");
    }

    @Test
    void changePassword_userNotFound_throwsRuntimeException() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.changePassword(99L, "any", "new"))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── addRole / removeRole ─────────────────────────────────────────────────────

    @Test
    void addRole_existingUser_addsRoleAndSaves() {
        User user = activeUser(); // has CALL_AGENT
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenReturn(user);

        auth.addRole(1L, Role.SALES_AGENT);

        verify(userRepo).save(argThat(u -> u.getRoles().contains(Role.SALES_AGENT)));
    }

    @Test
    void addRole_userNotFound_throwsRuntimeException() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.addRole(99L, Role.ADMIN))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void removeRole_userHasMultipleRoles_removesRole() {
        User user = User.builder().id(1L).name("Test").email("test@test.com")
                .password("hash").active(true)
                .roles(new HashSet<>(Set.of(Role.CALL_AGENT, Role.SALES_AGENT)))
                .mustChangePassword(false).build();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenReturn(user);

        auth.removeRole(1L, Role.SALES_AGENT);

        verify(userRepo).save(argThat(u -> !u.getRoles().contains(Role.SALES_AGENT)));
    }

    @Test
    void removeRole_lastRole_throwsRuntimeException() {
        User user = activeUser(); // only CALL_AGENT
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.removeRole(1L, Role.CALL_AGENT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("rol");
    }

    // ─── listUsers / getUserById / deactivateUser ──────────────────────────────

    @Test
    void listUsers_returnsAllMappedToResponse() {
        User u1 = activeUser();
        User u2 = User.builder().id(2L).name("Other").email("other@test.com")
                .password("hash2").active(true).roles(Set.of(Role.ADMIN))
                .mustChangePassword(false).build();
        when(userRepo.findAll()).thenReturn(List.of(u1, u2));

        List<UserResponse> result = auth.listUsers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserResponse::getEmail)
                .containsExactlyInAnyOrder("test@test.com", "other@test.com");
    }

    @Test
    void getUserById_exists_returnsResponse() {
        User user = activeUser();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        UserResponse resp = auth.getUserById(1L);

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void getUserById_notFound_throwsRuntimeException() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.getUserById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Usuario");
    }

    @Test
    void deactivateUser_callsRepoDeactivate() {
        auth.deactivateUser(1L);

        verify(userRepo).deactivate(1L);
    }
}
