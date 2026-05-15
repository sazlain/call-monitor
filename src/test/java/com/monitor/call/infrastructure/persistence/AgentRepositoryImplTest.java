package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.models.Agent;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGroupEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.AgentRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentGroupJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentRepositoryImplTest {

    @Mock private AgentJpaRepository agentRepo;
    @Mock private AgentGroupJpaRepository groupRepo;
    @Mock private UserJpaRepository userRepo;
    @InjectMocks private AgentRepositoryImpl repo;

    private UserEntity buildUserEntity(Long id, String name) {
        return UserEntity.builder().id(id).name(name).email(name + "@test.com")
                .password("hash").active(true).build();
    }

    private void stubUser(Long userId) {
        when(userRepo.findById(userId)).thenReturn(Optional.of(buildUserEntity(userId, "User" + userId)));
    }

    private AgentEntity buildEntity(Long id, Long userId, String ext) {
        return AgentEntity.builder().id(id).userId(userId).extension(ext).active(true).build();
    }

    private Agent buildDomain(Long id, String ext) {
        return Agent.builder().id(id).userId(10L).extension(ext).active(true).build();
    }

    // ─── save without group ────────────────────────────────────────────────

    @Test
    void save_withoutGroupId_savesAndReturnsDomain() {
        AgentEntity entity = buildEntity(1L, 10L, "1001");
        when(agentRepo.save(any())).thenReturn(entity);

        Agent result = repo.save(buildDomain(null, "1001"));

        assertThat(result.getExtension()).isEqualTo("1001");
        verify(agentRepo).save(any());
        verify(groupRepo, never()).findById(any());
    }

    // ─── save with group ───────────────────────────────────────────────────

    @Test
    void save_withGroupId_setsGroupAndSaves() {
        AgentGroupEntity group = AgentGroupEntity.builder().id(5L).name("Sales").adminId(1L).active(true).build();
        AgentEntity entity = buildEntity(1L, 10L, "1001");
        entity.setGroup(group);
        when(groupRepo.findById(5L)).thenReturn(Optional.of(group));
        when(agentRepo.save(any())).thenReturn(entity);

        Agent domain = Agent.builder().id(null).userId(10L).extension("1001").active(true).groupId(5L).build();
        Agent result = repo.save(domain);

        assertThat(result.getGroupId()).isEqualTo(5L);
        verify(groupRepo).findById(5L);
    }

    @Test
    void save_withGroupIdNotFound_throwsException() {
        when(groupRepo.findById(99L)).thenReturn(Optional.empty());

        Agent domain = Agent.builder().userId(10L).extension("1001").active(true).groupId(99L).build();

        assertThatThrownBy(() -> repo.save(domain))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Grupo no encontrado");
    }

    // ─── findById ──────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsMappedAgent() {
        when(agentRepo.findById(1L)).thenReturn(Optional.of(buildEntity(1L, 10L, "1001")));

        Optional<Agent> result = repo.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getExtension()).isEqualTo("1001");
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());
        assertThat(repo.findById(99L)).isEmpty();
    }

    // ─── findByExtension ───────────────────────────────────────────────────

    @Test
    void findByExtension_found_returnsMappedAgent() {
        when(agentRepo.findByExtension("1001")).thenReturn(Optional.of(buildEntity(1L, 10L, "1001")));

        Optional<Agent> result = repo.findByExtension("1001");

        assertThat(result).isPresent();
        assertThat(result.get().getExtension()).isEqualTo("1001");
    }

    // ─── findByUserId ──────────────────────────────────────────────────────

    @Test
    void findByUserId_found_returnsMappedAgent() {
        when(agentRepo.findByUserId(10L)).thenReturn(Optional.of(buildEntity(1L, 10L, "1001")));

        Optional<Agent> result = repo.findByUserId(10L);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(10L);
    }

    // ─── findByGroupId ─────────────────────────────────────────────────────

    @Test
    void findByGroupId_returnsActiveAgents() {
        when(agentRepo.findByGroupIdAndActiveTrue(5L))
                .thenReturn(List.of(buildEntity(1L, 10L, "1001"), buildEntity(2L, 11L, "1002")));

        List<Agent> result = repo.findByGroupId(5L);

        assertThat(result).hasSize(2);
    }

    // ─── findByAdminId ─────────────────────────────────────────────────────

    @Test
    void findByAdminId_returnsAgentList() {
        when(agentRepo.findByAdminId(1L)).thenReturn(List.of(buildEntity(1L, 10L, "1001")));

        List<Agent> result = repo.findByAdminId(1L);

        assertThat(result).hasSize(1);
    }

    // ─── findExtensionsByGroupId ────────────────────────────────────────────

    @Test
    void findExtensionsByGroupId_returnsExtensionList() {
        when(agentRepo.findExtensionsByGroupId(5L)).thenReturn(List.of("1001", "1002"));

        List<String> result = repo.findExtensionsByGroupId(5L);

        assertThat(result).containsExactly("1001", "1002");
    }

    // ─── findExtensionsByAdminId ───────────────────────────────────────────

    @Test
    void findExtensionsByAdminId_returnsExtensionList() {
        when(agentRepo.findExtensionsByAdminId(1L)).thenReturn(List.of("1001"));

        List<String> result = repo.findExtensionsByAdminId(1L);

        assertThat(result).containsExactly("1001");
    }

    // ─── existsByExtension ─────────────────────────────────────────────────

    @Test
    void existsByExtension_delegatesToJpa() {
        when(agentRepo.existsByExtension("1001")).thenReturn(true);
        assertThat(repo.existsByExtension("1001")).isTrue();
    }

    // ─── deactivate ────────────────────────────────────────────────────────

    @Test
    void deactivate_agentExists_setsActiveFalse() {
        AgentEntity entity = buildEntity(1L, 10L, "1001");
        when(agentRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(agentRepo.save(any())).thenReturn(entity);

        repo.deactivate(1L);

        assertThat(entity.getActive()).isFalse();
        verify(agentRepo).save(entity);
    }

    @Test
    void deactivate_notFound_doesNothing() {
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());

        repo.deactivate(99L);

        verify(agentRepo, never()).save(any());
    }
}
