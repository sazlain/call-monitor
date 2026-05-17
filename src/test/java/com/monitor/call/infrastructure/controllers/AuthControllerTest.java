package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.ports.in.AuthUseCases;
import com.monitor.call.domain.responses.LoginResponse;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.infrastructure.adapters.in.controllers.AuthController;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = AuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
@TestPropertySource(properties = "app.admin.secret=test-secret")
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AuthUseCases authUseCases;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserJpaRepository userJpaRepository;
    @MockitoBean private SimpMessagingTemplate ws;

    @org.springframework.beans.factory.annotation.Value("${app.admin.secret:test-secret}")
    String adminSecret;

    // ─── POST /api/auth/login ─────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithToken() throws Exception {
        when(authUseCases.login("ana@test.com", "Pass1234"))
                .thenReturn(LoginResponse.builder().token("jwt-token").build());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"ana@test.com","password":"Pass1234"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_invalidBody_missingEmail_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password":"Pass1234"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidBody_missingPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"ana@test.com"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_useCaseThrowsRuntimeException_returns404() throws Exception {
        when(authUseCases.login(anyString(), anyString()))
                .thenThrow(new RuntimeException("User no encontrado"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"no@exist.com","password":"Pass1234"}
                        """))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/auth/register ──────────────────────────────────────────────

    @Test
    void register_validSecretAndBody_returns201() throws Exception {
        UserResponse resp = UserResponse.builder()
                .id(1L).name("Admin").email("admin@test.com")
                .active(true).roles(Set.of(Role.ADMIN))
                .mustChangePassword(false)
                .build();
        when(authUseCases.registerAdmin("Admin", "admin@test.com", "Pass1234!")).thenReturn(resp);

        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Admin-Secret", "test-secret")
                .content("""
                        {"name":"Admin","email":"admin@test.com","password":"Pass1234!"}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    void register_wrongSecret_returns403() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Admin-Secret", "wrong-secret")
                .content("""
                        {"name":"Admin","email":"admin@test.com","password":"Pass1234!"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_missingHeader_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Admin","email":"admin@test.com","password":"Pass1234!"}
                        """))
                .andExpect(status().isBadRequest());
    }

    // ─── GET /api/auth/users ──────────────────────────────────────────────────

    @Test
    void listUsers_returnsUserList() throws Exception {
        UserResponse user = UserResponse.builder()
                .id(1L).name("Ana").email("ana@test.com")
                .active(true).roles(Set.of(Role.ADMIN)).mustChangePassword(false)
                .build();
        when(authUseCases.listUsers()).thenReturn(List.of(user));

        mvc.perform(get("/api/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ana@test.com"));
    }

    // ─── GET /api/auth/users/{userId} ─────────────────────────────────────────

    @Test
    void getUser_exists_returnsUser() throws Exception {
        UserResponse user = UserResponse.builder()
                .id(1L).name("Ana").email("ana@test.com")
                .active(true).roles(Set.of(Role.ADMIN)).mustChangePassword(false)
                .build();
        when(authUseCases.getUserById(1L)).thenReturn(user);

        mvc.perform(get("/api/auth/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        when(authUseCases.getUserById(99L))
                .thenThrow(new RuntimeException("User no encontrado"));

        mvc.perform(get("/api/auth/users/99"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/auth/change-password ──────────────────────────────────────

    @Test
    void changePassword_validRequest_returns204() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(1L);

        mvc.perform(post("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer valid-token")
                .content("""
                        {"currentPassword":"OldPass1","newPassword":"NewPass1"}
                        """))
                .andExpect(status().isNoContent());
    }

    // ─── POST /api/auth/users/{userId}/roles ──────────────────────────────────

    @Test
    void addRole_validRequest_returnsUpdatedUser() throws Exception {
        UserResponse resp = UserResponse.builder()
                .id(1L).name("Ana").email("ana@test.com")
                .active(true).roles(Set.of(Role.ADMIN, Role.SALES_AGENT)).mustChangePassword(false)
                .build();
        when(authUseCases.addRole(1L, Role.SALES_AGENT)).thenReturn(resp);

        mvc.perform(post("/api/auth/users/1/roles")
                .param("role", "SALES_AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ─── DELETE /api/auth/users/{userId}/roles/{role} ─────────────────────────

    @Test
    void removeRole_validRequest_returnsUpdatedUser() throws Exception {
        UserResponse resp = UserResponse.builder()
                .id(1L).name("Ana").email("ana@test.com")
                .active(true).roles(Set.of(Role.ADMIN)).mustChangePassword(false)
                .build();
        when(authUseCases.removeRole(1L, Role.SALES_AGENT)).thenReturn(resp);

        mvc.perform(delete("/api/auth/users/1/roles/SALES_AGENT"))
                .andExpect(status().isOk());
    }

    // ─── DELETE /api/auth/users/{userId} ─────────────────────────────────────

    @Test
    void deactivateUser_validRequest_returns204() throws Exception {
        mvc.perform(delete("/api/auth/users/1"))
                .andExpect(status().isNoContent());
    }
}
