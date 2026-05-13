package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.enums.CallResult;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallTypificationEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.CallTypificationRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.CallTypificationJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallTypificationRepositoryImplTest {

    @Mock private CallTypificationJpaRepository jpaRepo;
    @InjectMocks private CallTypificationRepositoryImpl repo;

    private CallTypificationEntity buildEntity(Long id, String callId, CallResult result) {
        return CallTypificationEntity.builder()
                .id(id).callId(callId).agentId(10L).leadId(5L)
                .result(result).contactName("Contact").contactPhone("555")
                .build();
    }

    private CallTypification buildDomain(String callId, CallResult result) {
        return CallTypification.builder()
                .callId(callId).agentId(10L).leadId(5L)
                .result(result).contactName("Contact").contactPhone("555")
                .build();
    }

    // ─── save ──────────────────────────────────────────────────────────────

    @Test
    void save_delegatesToJpaAndMapsDomain() {
        when(jpaRepo.save(any())).thenReturn(buildEntity(1L, "CALL-001", CallResult.SALE));

        CallTypification result = repo.save(buildDomain("CALL-001", CallResult.SALE));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getCallId()).isEqualTo("CALL-001");
        assertThat(result.getResult()).isEqualTo(CallResult.SALE);
        verify(jpaRepo).save(any());
    }

    // ─── findByCallId ──────────────────────────────────────────────────────

    @Test
    void findByCallId_found_returnsMappedTypification() {
        when(jpaRepo.findByCallId("CALL-001"))
                .thenReturn(Optional.of(buildEntity(1L, "CALL-001", CallResult.CALLBACK)));

        Optional<CallTypification> result = repo.findByCallId("CALL-001");

        assertThat(result).isPresent();
        assertThat(result.get().getResult()).isEqualTo(CallResult.CALLBACK);
    }

    @Test
    void findByCallId_notFound_returnsEmpty() {
        when(jpaRepo.findByCallId("NO-CALL")).thenReturn(Optional.empty());
        assertThat(repo.findByCallId("NO-CALL")).isEmpty();
    }

    // ─── existsByCallId ────────────────────────────────────────────────────

    @Test
    void existsByCallId_delegatesToJpa() {
        when(jpaRepo.existsByCallId("CALL-001")).thenReturn(true);
        assertThat(repo.existsByCallId("CALL-001")).isTrue();
    }

    @Test
    void existsByCallId_notPresent_returnsFalse() {
        when(jpaRepo.existsByCallId("CALL-XYZ")).thenReturn(false);
        assertThat(repo.existsByCallId("CALL-XYZ")).isFalse();
    }

    // ─── findByAgentId ─────────────────────────────────────────────────────

    @Test
    void findByAgentId_returnsList() {
        when(jpaRepo.findByAgentId(10L))
                .thenReturn(List.of(buildEntity(1L, "CALL-001", CallResult.SALE)));

        List<CallTypification> result = repo.findByAgentId(10L);

        assertThat(result).hasSize(1);
    }

    // ─── findByLeadId ──────────────────────────────────────────────────────

    @Test
    void findByLeadId_returnsList() {
        when(jpaRepo.findByLeadId(5L))
                .thenReturn(List.of(
                        buildEntity(1L, "CALL-001", CallResult.INTERESTED),
                        buildEntity(2L, "CALL-002", CallResult.CALLBACK)));

        List<CallTypification> result = repo.findByLeadId(5L);

        assertThat(result).hasSize(2);
    }

    // ─── findByAgentAndPeriod ──────────────────────────────────────────────

    @Test
    void findByAgentAndPeriod_delegatesToJpa() {
        OffsetDateTime from = OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to   = OffsetDateTime.of(2026, 5, 31, 23, 59, 59, 0, ZoneOffset.UTC);
        when(jpaRepo.findByAgentAndPeriod(10L, from, to))
                .thenReturn(List.of(buildEntity(1L, "CALL-001", CallResult.SALE)));

        List<CallTypification> result = repo.findByAgentAndPeriod(10L, from, to);

        assertThat(result).hasSize(1);
    }

    // ─── countByResultForAgent ─────────────────────────────────────────────

    @Test
    void countByResultForAgent_returnsRawRows() {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        OffsetDateTime to   = OffsetDateTime.now(ZoneOffset.UTC);
        Object[] row = new Object[]{"SALE", 5L};
        when(jpaRepo.countByResultForAgent(10L, from, to)).thenReturn(List.<Object[]>of(row));

        List<Object[]> result = repo.countByResultForAgent(10L, from, to);

        assertThat(result).hasSize(1);
    }

    // ─── findUntypifiedCallIds ─────────────────────────────────────────────

    @Test
    void findUntypifiedCallIds_returnsCallIdList() {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        OffsetDateTime to   = OffsetDateTime.now(ZoneOffset.UTC);
        when(jpaRepo.findUntypifiedCallIds(1L, from, to)).thenReturn(List.of("CALL-001", "CALL-002"));

        List<String> result = repo.findUntypifiedCallIds(1L, from, to);

        assertThat(result).containsExactly("CALL-001", "CALL-002");
    }
}
