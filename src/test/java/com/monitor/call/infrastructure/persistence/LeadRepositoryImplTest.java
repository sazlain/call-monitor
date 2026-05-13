package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.LeadRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadRepositoryImplTest {

    @Mock private LeadJpaRepository jpaRepo;
    @InjectMocks private LeadRepositoryImpl repo;

    private LeadEntity buildEntity(Long id, String name, LeadStatus status) {
        return LeadEntity.builder()
                .id(id).contactName(name).contactPhone("5551000")
                .leadSource("WEB").leadDate(LocalDate.now())
                .ownerId(5L).status(status).build();
    }

    private Lead buildDomain(Long id, String name, LeadStatus status) {
        return Lead.builder()
                .id(id).contactName(name).contactPhone("5551000")
                .leadSource("WEB").leadDate(LocalDate.now())
                .ownerId(5L).status(status).build();
    }

    // ─── save ──────────────────────────────────────────────────────────────

    @Test
    void save_delegatesToJpaAndMapsDomain() {
        when(jpaRepo.save(any())).thenReturn(buildEntity(1L, "Juan", LeadStatus.NEW));

        Lead result = repo.save(buildDomain(null, "Juan", LeadStatus.NEW));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getContactName()).isEqualTo("Juan");
        verify(jpaRepo).save(any());
    }

    // ─── saveAll ───────────────────────────────────────────────────────────

    @Test
    void saveAll_savesAllAndMapsResults() {
        when(jpaRepo.saveAll(any())).thenReturn(List.of(
                buildEntity(1L, "Juan", LeadStatus.NEW),
                buildEntity(2L, "Ana", LeadStatus.NEW)));

        List<Lead> result = repo.saveAll(List.of(
                buildDomain(null, "Juan", LeadStatus.NEW),
                buildDomain(null, "Ana", LeadStatus.NEW)));

        assertThat(result).hasSize(2);
    }

    // ─── findById ──────────────────────────────────────────────────────────

    @Test
    void findById_found_returnsMappedLead() {
        when(jpaRepo.findById(1L)).thenReturn(Optional.of(buildEntity(1L, "Juan", LeadStatus.NEW)));

        Optional<Lead> result = repo.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getContactName()).isEqualTo("Juan");
    }

    @Test
    void findById_notFound_returnsEmpty() {
        when(jpaRepo.findById(99L)).thenReturn(Optional.empty());
        assertThat(repo.findById(99L)).isEmpty();
    }

    // ─── findByOwnerId ─────────────────────────────────────────────────────

    @Test
    void findByOwnerId_returnsList() {
        when(jpaRepo.findByOwnerId(5L)).thenReturn(List.of(buildEntity(1L, "Juan", LeadStatus.NEW)));

        List<Lead> result = repo.findByOwnerId(5L);

        assertThat(result).hasSize(1);
    }

    // ─── findByOwnerIdAndStatus ────────────────────────────────────────────

    @Test
    void findByOwnerIdAndStatus_returnsFilteredList() {
        when(jpaRepo.findByOwnerIdAndStatus(5L, LeadStatus.CALLBACK))
                .thenReturn(List.of(buildEntity(2L, "Ana", LeadStatus.CALLBACK)));

        List<Lead> result = repo.findByOwnerIdAndStatus(5L, LeadStatus.CALLBACK);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(LeadStatus.CALLBACK);
    }

    // ─── findByOwnerAndDateRange ────────────────────────────────────────────

    @Test
    void findByOwnerAndDateRange_delegatesToJpa() {
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        when(jpaRepo.findByOwnerAndDateRange(5L, from, to))
                .thenReturn(List.of(buildEntity(1L, "Juan", LeadStatus.NEW)));

        List<Lead> result = repo.findByOwnerAndDateRange(5L, from, to);

        assertThat(result).hasSize(1);
    }

    // ─── findAssignedPendingLeads ───────────────────────────────────────────

    @Test
    void findAssignedPendingLeads_returnsList() {
        when(jpaRepo.findAssignedPendingLeads(10L))
                .thenReturn(List.of(buildEntity(3L, "Pedro", LeadStatus.PENDING)));

        List<Lead> result = repo.findAssignedPendingLeads(10L);

        assertThat(result).hasSize(1);
    }

    // ─── findPendingCallbacks ──────────────────────────────────────────────

    @Test
    void findPendingCallbacks_delegatesToJpa() {
        when(jpaRepo.findPendingCallbacks(5L, 10L))
                .thenReturn(List.of(buildEntity(4L, "Maria", LeadStatus.CALLBACK)));

        List<Lead> result = repo.findPendingCallbacks(5L, 10L);

        assertThat(result).hasSize(1);
    }

    // ─── countByStatusForOwner ─────────────────────────────────────────────

    @Test
    void countByStatusForOwner_returnsRawRows() {
        Object[] row = new Object[]{"NEW", 5L};
        when(jpaRepo.countByStatusForOwner(5L)).thenReturn(List.<Object[]>of(row));

        List<Object[]> result = repo.countByStatusForOwner(5L);

        assertThat(result).hasSize(1);
    }

    // ─── findActiveByPhone ─────────────────────────────────────────────────

    @Test
    void findActiveByPhone_found_returnsMappedLead() {
        when(jpaRepo.findActiveByContactPhone("5551000"))
                .thenReturn(List.of(buildEntity(1L, "Juan", LeadStatus.NEW)));

        Optional<Lead> result = repo.findActiveByPhone("5551000");

        assertThat(result).isPresent();
        assertThat(result.get().getContactPhone()).isEqualTo("5551000");
    }

    @Test
    void findActiveByPhone_notFound_returnsEmpty() {
        when(jpaRepo.findActiveByContactPhone("0000")).thenReturn(List.of());

        assertThat(repo.findActiveByPhone("0000")).isEmpty();
    }

    // ─── updateStatus ──────────────────────────────────────────────────────

    @Test
    void updateStatus_leadExists_updatesStatusAndSaves() {
        LeadEntity entity = buildEntity(1L, "Juan", LeadStatus.NEW);
        when(jpaRepo.findById(1L)).thenReturn(Optional.of(entity));
        when(jpaRepo.save(any())).thenReturn(entity);

        repo.updateStatus(1L, LeadStatus.CONVERTED);

        assertThat(entity.getStatus()).isEqualTo(LeadStatus.CONVERTED);
        verify(jpaRepo).save(entity);
    }

    @Test
    void updateStatus_notFound_doesNothing() {
        when(jpaRepo.findById(99L)).thenReturn(Optional.empty());

        repo.updateStatus(99L, LeadStatus.DISCARDED);

        verify(jpaRepo, never()).save(any());
    }
}
