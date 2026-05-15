package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.License;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.LicenseRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.LoginResponse;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.exceptions.SupportMessages;
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
    @Mock private AgentRepositoryPort agentRepo;
    @Mock private LicenseRepositoryPort licenseRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private SupportMessages supportMessages;

    @InjectMocks
    private AuthImpl auth;

    private User activeUser(Role role) {
        return User.builder()
                .id(1L).name("Test User").email("test@test.com")
                .password("hashedPass").active(true)
                .roles(new HashSet<>(Set.of(role)))
                .mustChangePassword(false).build();
    }

    private void stubSuccessfulLogin(User user, String token) {
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(agentRepo.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(jwtUtil.generateToken(eq(user.getId()), eq(user.getEmail()), any(), isNull())).thenReturn(token);
        when(jwtUtil.getExpirationMinutes()).thenReturn(60L);
    }

    @Test
    void login_validCredentials_returnsLoginResponse() {
        User user = activeUser(Role.CALL_AGENT);
        stubSuccessfulLogin(user, "jwt-token");

        LoginResponse resp = auth.login("test@test.com", "pass123");

        assertThat(resp.getToken()).isEqualTo("jwt-token");
        assertThat(resp.getUserId()).isEqualTo(1L);
        assertThat(resp.getEmail()).isEqualTo("test@test.com");
        assertThat(resp.getName()).isEqualTo("Test User");
    }

    @Test
    void login_agentUser_includesExtensionInResponse() {
        User user = activeUser(Role.CALL_AGENT);
        Agent agent = Agent.builder().id(10L).userId(1L).extension("1001").active(true).build();

        when(userRepo.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "hashedPass")).thenReturn(true);
        when(agentRepo.findByUserId(1L)).thenReturn(Optional.of(agent));
        when(jwtUtil.generateToken(1L, "test@test.com", user.getRoles(), "1001")).thenReturn("jwt-ext");
        when(jwtUtil.getExpirationMinutes()).thenReturn(60L);

        LoginResponse resp = auth.login("test@test.com", "pass123");

        assertThat(resp.getExtension()).isEqualTo("1001");
        assertThat(resp.getToken()).isEqualTo("jwt-ext");
    }

    @Test
    void login_userNotFound_throwsRuntimeException() {
        when(userRepo.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login("notfound@test.com", "pass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("invalidas");
    }

    @Test
    void login_wrongPassword_throwsRuntimeException() {
        User user = activeUser(Role.CALL_AGENT);
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> auth.login("test@test.com", "wrongPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("invalidas");
    }

    @Test
    void login_inactiveAccount_throwsAccountDisabled() {
        User user = User.builder().id(1L).email("test@test.com").password("hash")
                .active(false).roles(Set.of(Role.ADMIN)).build();
        when(userRepo.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);

        assertThatThrownBy(() -> auth.login("test@test.com", "pass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("ACCOUNT_DISABLED");
    }

    @Test
    void login_licensePending_throwsLicensePending() {
        User user = activeUser(Role.ADMIN);
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(licenseRepo.findByAdminId(1L)).thenReturn(Optional.of(
                License.builder().status(LicenseStatus.PENDING).maxAgents(5).build()));

        assertThatThrownBy(() -> auth.login("test@test.com", "pass123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LICENSE_PENDING");
    }

    @Test
    void login_licenseExpired_throwsLicenseExpired() {
        User user = activeUser(Role.ADMIN);
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(licenseRepo.findByAdminId(1L)).thenReturn(Optional.of(
                License.builder().status(LicenseStatus.EXPIRED).maxAgents(5).build()));

        assertThatThrownBy(() -> auth.login("test@test.com", "pass123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LICENSE_EXPIRED");
    }

    @Test
    void login_licenseSuspended_throwsLicenseSuspended() {
        User user = activeUser(Role.ADMIN);
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(licenseRepo.findByAdminId(1L)).thenReturn(Optional.of(
                License.builder().status(LicenseStatus.SUSPENDED).maxAgents(5).build()));

        assertThatThrownBy(() -> auth.login("test@test.com", "pass123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("LICENSE_SUSPENDED");
    }

    @Test
    void login_superAdmin_bypassesLicenseCheck() {
        User superAdmin = User.builder().id(1L).email("sa@test.com").password("hash")
                .active(true).roles(Set.of(Role.SUPER_ADMIN)).mustChangePassword(false).build();
        when(userRepo.findByEmail("sa@test.com")).thenReturn(Optional.of(superAdmin));
        when(passwordEncoder.matches("pass", "hash")).thenReturn(true);
        when(agentRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("sa-token");
        when(jwtUtil.getExpirationMinutes()).thenReturn(60L);

        LoginResponse resp = auth.login("sa@test.com", "pass");

        assertThat(resp.getToken()).isEqualTo("sa-token");
        verify(licenseRepo, never()).findByAdminId(any());
    }

    @Test
    void login_noLicense_allowsAccess() {
        User user = activeUser(Role.ADMIN);
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(licenseRepo.findByAdminId(1L)).thenReturn(Optional.empty());
        when(agentRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("token");
        when(jwtUtil.getExpirationMinutes()).thenReturn(60L);

        assertThatCode(() -> auth.login("test@test.com", "pass123")).doesNotThrowAnyException();
    }

    @Test
    void login_licenseActive_allowsAccess() {
        User user = activeUser(Role.ADMIN);
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(licenseRepo.findByAdminId(1L)).thenReturn(Optional.of(
                License.builder().status(LicenseStatus.ACTIVE).maxAgents(10).build()));
        when(agentRepo.findByUserId(1L)).thenReturn(Optional.empty());
        when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("token");
        when(jwtUtil.getExpirationMinutes()).thenReturn(60L);

        assertThatCode(() -> auth.login("test@test.com", "pass123")).doesNotThrowAnyException();
    }

    @Test
    void registerAdmin_newEmail_savesUser() {
        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("hashed");
        when(userRepo.save(any())).thenReturn(User.builder().id(2L).name("Admin")
                .email("new@test.com").active(true).roles(Set.of(Role.ADMIN)).build());

        UserResponse resp = auth.registerAdmin("Admin", "new@test.com", "pass");

        assertThat(resp.getEmail()).isEqualTo("new@test.com");
    }

    @Test
    void registerAdmin_duplicateEmail_throwsRuntimeException() {
        when(userRepo.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> auth.registerAdmin("Admin", "dup@test.com", "pass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("registrado");
    }

    @Test
    void changePassword_correctCurrent_updatesPassword() {
        User user = activeUser(Role.CALL_AGENT);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", user.getPassword())).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("newHashed");
        when(userRepo.save(any())).thenReturn(user);

        assertThatCode(() -> auth.changePassword(1L, "oldPass", "newPass"))
                .doesNotThrowAnyException();

        verify(userRepo).save(argThat(u -> !u.getMustChangePassword()));
    }

    @Test
    void changePassword_wrongCurrent_throwsRuntimeException() {
        User user = activeUser(Role.CALL_AGENT);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> auth.changePassword(1L, "wrong", "newPass"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("contrasena actual");
    }

    @Test
    void changePassword_userNotFound_throwsRuntimeException() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.changePassword(99L, "pass", "newPass"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void addRole_addsRoleToUser() {
        User user = activeUser(Role.CALL_AGENT);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auth.addRole(1L, Role.ADMIN);

        verify(userRepo).save(argThat(u -> u.getRoles().contains(Role.ADMIN)));
    }

    @Test
    void removeRole_lastRole_throwsRuntimeException() {
        User user = activeUser(Role.CALL_AGENT);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> auth.removeRole(1L, Role.CALL_AGENT))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("al menos un rol");
    }

    @Test
    void removeRole_multipleRoles_removesRole() {
        User user = User.builder().id(1L).email("t@t.com").password("h").active(true)
                .roles(new HashSet<>(Set.of(Role.CALL_AGENT, Role.ADMIN))).build();
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auth.removeRole(1L, Role.ADMIN);

        verify(userRepo).save(argThat(u -> !u.getRoles().contains(Role.ADMIN)));
    }

    @Test
    void deactivateUser_callsRepositoryDeactivate() {
        auth.deactivateUser(5L);
        verify(userRepo).deactivate(5L);
    }

    @Test
    void listUsers_returnsAllUsers() {
        User user = activeUser(Role.CALL_AGENT);
        when(userRepo.findAll()).thenReturn(List.of(user));

        var result = auth.listUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("test@test.com");
    }

    @Test
    void getUserById_found_returnsUser() {
        User user = activeUser(Role.CALL_AGENT);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));

        UserResponse resp = auth.getUserById(1L);

        assertThat(resp.getId()).isEqualTo(1L);
    }

    @Test
    void getUserById_notFound_throwsRuntimeException() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.getUserById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no encontrado");
    }
}
