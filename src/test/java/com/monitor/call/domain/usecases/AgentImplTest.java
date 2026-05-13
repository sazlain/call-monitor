package com.monitor.call.domain.usecases;

import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.UserRepositoryPort;
import com.monitor.call.domain.responses.AgentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentImplTest {

    @Mock private AgentRepositoryPort agentRepo;
    @Mock private UserRepositoryPort userRepo;
    @Mock private UserJpaRepository userJpaRepo;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AgentImpl agentImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Agent buildAgent(Long id, Long userId, String extension) {
        return Agent.builder().id(id).userId(userId).extension(extension).active(true).build();
    }

    private User buildUser(Long id, String name, String email) {
        return User.builder().id(id).name(name).email(email).active(true).build();
    }

    private UserEntity buildUserEntity(Long id, String name, String email) {
        return UserEntity.builder().id(id).name(name).email(email).password("hash").active(true).build();
    }

    private void stubUserJpaForAgent(Agent agent, String name) {
        when(userJpaRepo.findAllById(List.of(agent.getUserId())))
                .thenReturn(List.of(buildUserEntity(agent.getUserId(), name, name + "@test.com")));
    }

    // ─── createAgent ─────────────────────────────────────────────────────────

    @Test
    void createAgent_newEmailAndExtension_savesUserAndAgent() {
        when(userRepo.existsByEmail("ana@test.com")).thenReturn(false);
        when(agentRepo.existsByExtension("1001")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        User savedUser = buildUser(10L, "Ana", "ana@test.com");
        when(userRepo.save(any())).thenReturn(savedUser);

        Agent savedAgent = buildAgent(20L, 10L, "1001");
        when(agentRepo.save(any())).thenReturn(savedAgent);
        when(userJpaRepo.findAllById(List.of(10L)))
                .thenReturn(List.of(buildUserEntity(10L, "Ana", "ana@test.com")));

        AgentResponse resp = agentImpl.createAgent("Ana", "ana@test.com", "1001", null, 1L);

        assertThat(resp.getId()).isEqualTo(20L);
        assertThat(resp.getExtension()).isEqualTo("1001");
        verify(userRepo).save(any());
        verify(agentRepo).save(any());
    }

    @Test
    void createAgent_duplicateEmail_throwsRuntimeException() {
        when(userRepo.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> agentImpl.createAgent("Dup", "dup@test.com", "1002", null, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("email");

        verify(agentRepo, never()).save(any());
    }

    @Test
    void createAgent_duplicateExtension_throwsRuntimeException() {
        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(agentRepo.existsByExtension("1001")).thenReturn(true);

        assertThatThrownBy(() -> agentImpl.createAgent("New", "new@test.com", "1001", null, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("extension");

        verify(userRepo, never()).save(any());
    }

    // ─── getAgent ─────────────────────────────────────────────────────────────

    @Test
    void getAgent_exists_returnsResponse() {
        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(userJpaRepo.findAllById(List.of(100L)))
                .thenReturn(List.of(buildUserEntity(100L, "Ana", "ana@test.com")));

        AgentResponse resp = agentImpl.getAgent(10L);

        assertThat(resp.getId()).isEqualTo(10L);
        assertThat(resp.getExtension()).isEqualTo("1001");
    }

    @Test
    void getAgent_notFound_throwsRuntimeException() {
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentImpl.getAgent(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Agente");
    }

    // ─── getAgentByExtension ──────────────────────────────────────────────────

    @Test
    void getAgentByExtension_exists_returnsResponse() {
        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentRepo.findByExtension("1001")).thenReturn(Optional.of(agent));
        when(userJpaRepo.findAllById(List.of(100L)))
                .thenReturn(List.of(buildUserEntity(100L, "Ana", "ana@test.com")));

        AgentResponse resp = agentImpl.getAgentByExtension("1001");

        assertThat(resp.getExtension()).isEqualTo("1001");
    }

    @Test
    void getAgentByExtension_notFound_throwsRuntimeException() {
        when(agentRepo.findByExtension("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentImpl.getAgentByExtension("9999"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Agente");
    }

    // ─── listAgentsByGroup ────────────────────────────────────────────────────

    @Test
    void listAgentsByGroup_returnsAgentsForGroup() {
        Agent a1 = buildAgent(1L, 10L, "1001");
        Agent a2 = buildAgent(2L, 11L, "1002");
        when(agentRepo.findByGroupId(5L)).thenReturn(List.of(a1, a2));
        when(userJpaRepo.findAllById(List.of(10L, 11L))).thenReturn(List.of(
                buildUserEntity(10L, "Ana", "ana@test.com"),
                buildUserEntity(11L, "Bob", "bob@test.com")));

        List<AgentResponse> result = agentImpl.listAgentsByGroup(5L);

        assertThat(result).hasSize(2);
    }

    @Test
    void listAgentsByGroup_empty_returnsEmptyList() {
        when(agentRepo.findByGroupId(5L)).thenReturn(List.of());
        when(userJpaRepo.findAllById(List.of())).thenReturn(List.of());

        List<AgentResponse> result = agentImpl.listAgentsByGroup(5L);

        assertThat(result).isEmpty();
    }

    // ─── listAgentsByAdmin ────────────────────────────────────────────────────

    @Test
    void listAgentsByAdmin_returnsAgentsForAdmin() {
        Agent agent = buildAgent(1L, 10L, "1001");
        when(agentRepo.findByAdminId(1L)).thenReturn(List.of(agent));
        when(userJpaRepo.findAllById(List.of(10L)))
                .thenReturn(List.of(buildUserEntity(10L, "Ana", "ana@test.com")));

        List<AgentResponse> result = agentImpl.listAgentsByAdmin(1L);

        assertThat(result).hasSize(1);
    }

    // ─── updateAgent ──────────────────────────────────────────────────────────

    @Test
    void updateAgent_sameExtension_updatesName() {
        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(agentRepo.save(any())).thenReturn(agent);
        when(userRepo.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Old", "old@test.com")));
        when(userJpaRepo.findAllById(List.of(100L)))
                .thenReturn(List.of(buildUserEntity(100L, "New Name", "old@test.com")));

        AgentResponse resp = agentImpl.updateAgent(10L, "New Name", "1001", 1L);

        verify(userRepo).save(any()); // name updated
        verify(agentRepo).save(any());
        assertThat(resp).isNotNull();
    }

    @Test
    void updateAgent_newExtensionNotTaken_updatesExtension() {
        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(agentRepo.existsByExtension("1002")).thenReturn(false);
        when(agentRepo.save(any())).thenReturn(agent);
        when(userRepo.findById(100L)).thenReturn(Optional.of(buildUser(100L, "Ana", "ana@test.com")));
        when(userJpaRepo.findAllById(List.of(100L)))
                .thenReturn(List.of(buildUserEntity(100L, "Ana", "ana@test.com")));

        agentImpl.updateAgent(10L, "Ana", "1002", 1L);

        verify(agentRepo).save(argThat(a -> "1002".equals(a.getExtension())));
    }

    @Test
    void updateAgent_newExtensionAlreadyTaken_throwsRuntimeException() {
        Agent agent = buildAgent(10L, 100L, "1001");
        when(agentRepo.findById(10L)).thenReturn(Optional.of(agent));
        when(agentRepo.existsByExtension("1002")).thenReturn(true);

        assertThatThrownBy(() -> agentImpl.updateAgent(10L, "Ana", "1002", 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("extension");
    }

    @Test
    void updateAgent_notFound_throwsRuntimeException() {
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentImpl.updateAgent(99L, "X", "1001", 1L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── deactivateAgent ──────────────────────────────────────────────────────

    @Test
    void deactivateAgent_exists_callsDeactivate() {
        when(agentRepo.findById(10L)).thenReturn(Optional.of(buildAgent(10L, 100L, "1001")));

        agentImpl.deactivateAgent(10L, 1L);

        verify(agentRepo).deactivate(10L);
    }

    @Test
    void deactivateAgent_notFound_throwsRuntimeException() {
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentImpl.deactivateAgent(99L, 1L))
                .isInstanceOf(RuntimeException.class);
    }
}
