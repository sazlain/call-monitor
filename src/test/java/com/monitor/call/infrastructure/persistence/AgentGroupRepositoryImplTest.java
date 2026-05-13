package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.models.AgentGroup;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGroupEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.AgentGroupRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentGroupJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentGroupRepositoryImplTest {

    @Mock private AgentGroupJpaRepository jpaRepo;
    @InjectMocks private AgentGroupRepositoryImpl repo;

    private AgentGroupEntity buildEntity(Long id, String name) {
        return AgentGroupEntity.builder().id(id).name(name).adminId(1L).active(true).build();
    }

    private AgentGroup buildDomain(Long id, String name) {
        return AgentGroup.builder().id(id).name(name).adminId(1L).active(true).build();
    }

    // ─── save ──────────────────────────────────────────────────────────────

    @Test
    void save_delegatesToJpaAndMapsResult() {
        when(jpaRepo.save(any())).thenReturn(buildEntity(1L, "Sales"));

        AgentGroup result = repo.save(buildDomain(null, "Sales"));

        assertThat(result.getName()).isEqualTo("Sales");
        assertThat(result.getId()).isEqualTo(1L);
        verify(jpaRepo).save(any());
    }

    @Test
    void save_withNullActive_defaultsToTrue() {
        AgentGroup domain = AgentGroup.builder().name("Support").adminId(1L).active(null).build();
        AgentGroupEntity saved = buildEntity(2L, "Support");
        when(jpaRepo.save(any())).thenReturn(saved);

        AgentGroup result = repo.save(domain);

        assertThat(result.getActive()).isTrue();
    }

    // ─── findById ──────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsMappedGroup() {
        when(jpaRepo.findById(1L)).thenReturn(Optional.of(buildEntity(1L, "Sales")));

        Optional<AgentGroup> result = repo.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Sales");
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(jpaRepo.findById(99L)).thenReturn(Optional.empty());
        assertThat(repo.findById(99L)).isEmpty();
    }

    // ─── findByIdAndAdminId ────────────────────────────────────────────────

    @Test
    void findByIdAndAdminId_found_returnsGroup() {
        when(jpaRepo.findByIdAndAdminId(1L, 1L)).thenReturn(Optional.of(buildEntity(1L, "Sales")));

        Optional<AgentGroup> result = repo.findByIdAndAdminId(1L, 1L);

        assertThat(result).isPresent();
    }

    @Test
    void findByIdAndAdminId_wrongAdmin_returnsEmpty() {
        when(jpaRepo.findByIdAndAdminId(1L, 99L)).thenReturn(Optional.empty());
        assertThat(repo.findByIdAndAdminId(1L, 99L)).isEmpty();
    }

    // ─── findByAdminId ─────────────────────────────────────────────────────

    @Test
    void findByAdminId_returnsActiveGroups() {
        when(jpaRepo.findByAdminIdAndActiveTrue(1L))
                .thenReturn(List.of(buildEntity(1L, "Sales"), buildEntity(2L, "Support")));

        List<AgentGroup> result = repo.findByAdminId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Sales");
    }

    // ─── existsByNameAndAdminId ────────────────────────────────────────────

    @Test
    void existsByNameAndAdminId_returnsTrue() {
        when(jpaRepo.existsByNameAndAdminId("Sales", 1L)).thenReturn(true);
        assertThat(repo.existsByNameAndAdminId("Sales", 1L)).isTrue();
    }

    // ─── deactivate ────────────────────────────────────────────────────────

    @Test
    void deactivate_groupExists_setsActiveFalse() {
        AgentGroupEntity entity = buildEntity(1L, "Sales");
        when(jpaRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(jpaRepo.save(any())).thenReturn(entity);

        repo.deactivate(1L);

        assertThat(entity.getActive()).isFalse();
        verify(jpaRepo).save(entity);
    }

    @Test
    void deactivate_notFound_doesNothing() {
        when(jpaRepo.findById(99L)).thenReturn(Optional.empty());

        repo.deactivate(99L);

        verify(jpaRepo, never()).save(any());
    }
}
