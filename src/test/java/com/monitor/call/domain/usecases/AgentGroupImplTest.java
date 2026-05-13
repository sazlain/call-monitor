package com.monitor.call.domain.usecases;

import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.AgentGroup;
import com.monitor.call.domain.ports.out.AgentGroupRepositoryPort;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.responses.AgentGroupResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentGroupImplTest {

    @Mock private AgentGroupRepositoryPort groupRepo;
    @Mock private AgentRepositoryPort agentRepo;
    @Mock private UserJpaRepository userJpaRepository;

    @InjectMocks
    private AgentGroupImpl agentGroupImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private AgentGroup buildGroup(Long id, String name, Long adminId) {
        return AgentGroup.builder()
                .id(id).name(name).description("desc")
                .adminId(adminId).active(true)
                .agents(List.of()).build();
    }

    private Agent buildAgent(Long id, Long userId, Long groupId) {
        return Agent.builder()
                .id(id).userId(userId).extension("100" + id)
                .active(true).groupId(groupId).build();
    }

    private UserEntity buildUserEntity(Long id, String name) {
        return UserEntity.builder().id(id).name(name).email(name + "@test.com")
                .password("hash").active(true).build();
    }

    // ─── createGroup ──────────────────────────────────────────────────────────────

    @Test
    void createGroup_newName_savesAndReturnsResponse() {
        when(groupRepo.existsByNameAndAdminId("Sales", 1L)).thenReturn(false);
        AgentGroup saved = buildGroup(10L, "Sales", 1L);
        when(groupRepo.save(any())).thenReturn(saved);
        when(agentRepo.findByGroupId(10L)).thenReturn(List.of());
        when(userJpaRepository.findAllById(any())).thenReturn(List.of());

        AgentGroupResponse resp = agentGroupImpl.createGroup("Sales", "desc", 1L);

        assertThat(resp.getName()).isEqualTo("Sales");
        assertThat(resp.getId()).isEqualTo(10L);
        verify(groupRepo).save(any());
    }

    @Test
    void createGroup_duplicateName_throwsRuntimeException() {
        when(groupRepo.existsByNameAndAdminId("Sales", 1L)).thenReturn(true);

        assertThatThrownBy(() -> agentGroupImpl.createGroup("Sales", "desc", 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("grupo");
    }

    // ─── getGroup ─────────────────────────────────────────────────────────────────

    @Test
    void getGroup_exists_returnsResponse() {
        AgentGroup group = buildGroup(10L, "Sales", 1L);
        when(groupRepo.findByIdAndAdminId(10L, 1L)).thenReturn(Optional.of(group));
        when(agentRepo.findByGroupId(10L)).thenReturn(List.of());
        when(userJpaRepository.findAllById(any())).thenReturn(List.of());

        AgentGroupResponse resp = agentGroupImpl.getGroup(10L, 1L);

        assertThat(resp.getId()).isEqualTo(10L);
        assertThat(resp.getName()).isEqualTo("Sales");
    }

    @Test
    void getGroup_notFound_throwsRuntimeException() {
        when(groupRepo.findByIdAndAdminId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGroupImpl.getGroup(99L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Grupo");
    }

    // ─── listGroupsByAdmin ────────────────────────────────────────────────────────

    @Test
    void listGroupsByAdmin_returnsAllGroupsForAdmin() {
        AgentGroup g1 = buildGroup(1L, "Group A", 5L);
        AgentGroup g2 = buildGroup(2L, "Group B", 5L);
        when(groupRepo.findByAdminId(5L)).thenReturn(List.of(g1, g2));
        when(agentRepo.findByGroupId(anyLong())).thenReturn(List.of());
        when(userJpaRepository.findAllById(any())).thenReturn(List.of());

        List<AgentGroupResponse> result = agentGroupImpl.listGroupsByAdmin(5L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AgentGroupResponse::getName)
                .containsExactlyInAnyOrder("Group A", "Group B");
    }

    @Test
    void listGroupsByAdmin_noGroups_returnsEmptyList() {
        when(groupRepo.findByAdminId(5L)).thenReturn(List.of());

        List<AgentGroupResponse> result = agentGroupImpl.listGroupsByAdmin(5L);

        assertThat(result).isEmpty();
    }

    // ─── updateGroup ──────────────────────────────────────────────────────────────

    @Test
    void updateGroup_exists_updatesAndSaves() {
        AgentGroup group = buildGroup(10L, "Old Name", 1L);
        when(groupRepo.findByIdAndAdminId(10L, 1L)).thenReturn(Optional.of(group));
        when(groupRepo.save(any())).thenReturn(buildGroup(10L, "New Name", 1L));
        when(agentRepo.findByGroupId(10L)).thenReturn(List.of());
        when(userJpaRepository.findAllById(any())).thenReturn(List.of());

        AgentGroupResponse resp = agentGroupImpl.updateGroup(10L, "New Name", "new desc", 1L);

        assertThat(resp.getName()).isEqualTo("New Name");
        verify(groupRepo).save(any());
    }

    @Test
    void updateGroup_notFound_throwsRuntimeException() {
        when(groupRepo.findByIdAndAdminId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGroupImpl.updateGroup(99L, "Name", "desc", 1L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── deactivateGroup ──────────────────────────────────────────────────────────

    @Test
    void deactivateGroup_exists_callsDeactivate() {
        when(groupRepo.findByIdAndAdminId(10L, 1L))
                .thenReturn(Optional.of(buildGroup(10L, "Sales", 1L)));

        agentGroupImpl.deactivateGroup(10L, 1L);

        verify(groupRepo).deactivate(10L);
    }

    @Test
    void deactivateGroup_notFound_throwsRuntimeException() {
        when(groupRepo.findByIdAndAdminId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGroupImpl.deactivateGroup(99L, 1L))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── assignAgentToGroup ───────────────────────────────────────────────────────

    @Test
    void assignAgentToGroup_validGroupAndAgent_updatesAgentGroupId() {
        AgentGroup group = buildGroup(10L, "Sales", 1L);
        Agent agent = buildAgent(20L, 100L, null);

        when(groupRepo.findByIdAndAdminId(10L, 1L)).thenReturn(Optional.of(group));
        when(agentRepo.findById(20L)).thenReturn(Optional.of(agent));
        when(agentRepo.save(any())).thenReturn(agent);

        // getGroup called at the end
        when(groupRepo.findByIdAndAdminId(10L, 1L)).thenReturn(Optional.of(group));
        when(agentRepo.findByGroupId(10L)).thenReturn(List.of(agent));
        UserEntity userEntity = buildUserEntity(100L, "Agent One");
        when(userJpaRepository.findAllById(List.of(100L))).thenReturn(List.of(userEntity));

        AgentGroupResponse resp = agentGroupImpl.assignAgentToGroup(10L, 20L, 1L);

        verify(agentRepo).save(argThat(a -> Long.valueOf(10L).equals(a.getGroupId())));
        assertThat(resp.getAgentCount()).isEqualTo(1);
    }

    @Test
    void assignAgentToGroup_agentNotFound_throwsRuntimeException() {
        when(groupRepo.findByIdAndAdminId(10L, 1L))
                .thenReturn(Optional.of(buildGroup(10L, "Sales", 1L)));
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGroupImpl.assignAgentToGroup(10L, 99L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Agente");
    }

    // ─── removeAgentFromGroup ─────────────────────────────────────────────────────

    @Test
    void removeAgentFromGroup_validGroupAndAgent_setsGroupIdToNull() {
        AgentGroup group = buildGroup(10L, "Sales", 1L);
        Agent agent = buildAgent(20L, 100L, 10L);

        when(groupRepo.findByIdAndAdminId(10L, 1L)).thenReturn(Optional.of(group));
        when(agentRepo.findById(20L)).thenReturn(Optional.of(agent));
        when(agentRepo.save(any())).thenReturn(agent);

        when(agentRepo.findByGroupId(10L)).thenReturn(List.of());
        when(userJpaRepository.findAllById(any())).thenReturn(List.of());

        agentGroupImpl.removeAgentFromGroup(10L, 20L, 1L);

        verify(agentRepo).save(argThat(a -> a.getGroupId() == null));
    }

    @Test
    void removeAgentFromGroup_agentNotFound_throwsRuntimeException() {
        when(groupRepo.findByIdAndAdminId(10L, 1L))
                .thenReturn(Optional.of(buildGroup(10L, "Sales", 1L)));
        when(agentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> agentGroupImpl.removeAgentFromGroup(10L, 99L, 1L))
                .isInstanceOf(RuntimeException.class);
    }
}
