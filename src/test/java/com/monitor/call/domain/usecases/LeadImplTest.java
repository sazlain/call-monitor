package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.ports.out.AgentRepositoryPort;
import com.monitor.call.domain.ports.out.LeadRepositoryPort;
import com.monitor.call.domain.responses.BulkLeadResponse;
import com.monitor.call.domain.responses.LeadResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.requests.CreateLeadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class LeadImplTest {

    @Mock private LeadRepositoryPort leadRepo;
    @Mock private UserJpaRepository userRepo;
    @Mock private AgentJpaRepository agentRepo;
    @Mock private AgentRepositoryPort agentRepositoryPort;
    @Mock private SystemConfigUseCases configUseCases;

    @InjectMocks
    private LeadImpl leadImpl;

    @BeforeEach
    void setUp() {
        // Por defecto el modo de asignación es MANUAL — round-robin no se activa
        // lenient() evita UnnecessaryStubbing en tests que no invocan createLead/createBulkLeads
        lenient().when(configUseCases.getValue(any(), eq("leads.assignment_mode"))).thenReturn("MANUAL");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private CreateLeadRequest buildRequest(String name, String phone) {
        return CreateLeadRequest.builder()
                .contactName(name).contactPhone(phone)
                .leadSource("WEB").notes("test note")
                .leadDate(LocalDate.now()).build();
    }

    private Lead buildSavedLead(Long id, Long ownerId, LeadStatus status) {
        return Lead.builder()
                .id(id).contactName("Contact " + id).contactPhone("555000" + id)
                .leadSource("WEB").ownerId(ownerId).status(status)
                .leadDate(LocalDate.now()).build();
    }

    private UserEntity buildUserEntity(Long id, String name) {
        return UserEntity.builder().id(id).name(name).email(name + "@test.com")
                .password("hash").active(true).build();
    }

    private AgentEntity buildAgentEntity(Long id, Long userId) {
        return AgentEntity.builder().id(id).userId(userId).extension("100" + id).active(true).build();
    }

    // ─── createLead ───────────────────────────────────────────────────────────────

    @Test
    void createLead_withoutStatusField_defaultsToPending() {
        // Sin status explícito → el default es PENDING (independientemente del agente asignado)
        CreateLeadRequest req = buildRequest("Juan", "5551234567");
        Lead saved = buildSavedLead(1L, 5L, LeadStatus.PENDING);
        when(leadRepo.save(any())).thenReturn(saved);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        LeadResponse resp = leadImpl.createLead(req, 5L);

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getStatus()).isEqualTo(LeadStatus.PENDING);
        verify(leadRepo).save(argThat(l -> l.getStatus() == LeadStatus.PENDING));
    }

    @Test
    void createLead_withExplicitStatus_usesProvidedStatus() {
        // Status explícito en el request → se usa tal cual
        CreateLeadRequest req = CreateLeadRequest.builder()
                .contactName("Ana").contactPhone("5559999").leadSource("REF")
                .status(LeadStatus.INTERESTED).build();
        Lead saved = buildSavedLead(2L, 5L, LeadStatus.INTERESTED);
        when(leadRepo.save(any())).thenReturn(saved);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        LeadResponse resp = leadImpl.createLead(req, 5L);

        assertThat(resp.getStatus()).isEqualTo(LeadStatus.INTERESTED);
        verify(leadRepo).save(argThat(l -> l.getStatus() == LeadStatus.INTERESTED));
    }

    @Test
    void createLead_withAssignedAgentNoStatus_defaultsToPending() {
        CreateLeadRequest req = CreateLeadRequest.builder()
                .contactName("Carlos").contactPhone("5558888")
                .assignedAgentId(10L).build();
        Lead saved = buildSavedLead(3L, 5L, LeadStatus.PENDING);
        when(leadRepo.save(any())).thenReturn(saved);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        LeadResponse resp = leadImpl.createLead(req, 5L);

        assertThat(resp.getStatus()).isEqualTo(LeadStatus.PENDING);
        verify(leadRepo).save(argThat(l -> l.getStatus() == LeadStatus.PENDING));
    }

    @Test
    void createLead_withoutLeadDate_usesToday() {
        CreateLeadRequest req = CreateLeadRequest.builder()
                .contactName("Pedro").contactPhone("5551111")
                .leadSource("PHONE").build(); // no leadDate
        Lead saved = buildSavedLead(3L, 5L, LeadStatus.NEW);
        when(leadRepo.save(any())).thenReturn(saved);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        leadImpl.createLead(req, 5L);

        verify(leadRepo).save(argThat(l -> l.getLeadDate() != null));
    }

    // ─── createBulkLeads ──────────────────────────────────────────────────────────

    @Test
    void createBulkLeads_allValid_returnsCreatedCount() {
        List<CreateLeadRequest> requests = List.of(
                buildRequest("Lead 1", "5551001"),
                buildRequest("Lead 2", "5551002"),
                buildRequest("Lead 3", "5551003"));
        when(leadRepo.saveAll(any())).thenReturn(List.of(
                buildSavedLead(1L, 5L, LeadStatus.NEW),
                buildSavedLead(2L, 5L, LeadStatus.NEW),
                buildSavedLead(3L, 5L, LeadStatus.NEW)));

        BulkLeadResponse resp = leadImpl.createBulkLeads(requests, 5L, null);

        assertThat(resp.getTotal()).isEqualTo(3);
        assertThat(resp.getCreated()).isEqualTo(3);
        assertThat(resp.getFailed()).isZero();
        assertThat(resp.getErrors()).isEmpty();
    }

    @Test
    void createBulkLeads_missingContactName_countedAsFailed() {
        List<CreateLeadRequest> requests = List.of(
                buildRequest("Valid", "5551001"),
                CreateLeadRequest.builder().contactPhone("5551002").build()); // no name
        when(leadRepo.saveAll(any())).thenReturn(
                List.of(buildSavedLead(1L, 5L, LeadStatus.NEW)));

        BulkLeadResponse resp = leadImpl.createBulkLeads(requests, 5L, null);

        assertThat(resp.getTotal()).isEqualTo(2);
        assertThat(resp.getCreated()).isEqualTo(1);
        assertThat(resp.getFailed()).isEqualTo(1);
        assertThat(resp.getErrors()).hasSize(1);
    }

    @Test
    void createBulkLeads_missingPhone_countedAsFailed() {
        // no phone → validation throws, toSave stays empty → saveAll not called
        List<CreateLeadRequest> requests = List.of(
                CreateLeadRequest.builder().contactName("No Phone").build());

        BulkLeadResponse resp = leadImpl.createBulkLeads(requests, 5L, null);

        assertThat(resp.getFailed()).isEqualTo(1);
        verify(leadRepo, never()).saveAll(any());
    }

    @Test
    void createBulkLeads_emptyList_returnsZeroCounts() {
        // empty input → saveAll never called
        BulkLeadResponse resp = leadImpl.createBulkLeads(List.of(), 5L, null);

        assertThat(resp.getTotal()).isZero();
        assertThat(resp.getCreated()).isZero();
        verify(leadRepo, never()).saveAll(any());
    }

    // ─── getLead ──────────────────────────────────────────────────────────────────

    @Test
    void getLead_exists_returnsResponse() {
        Lead lead = buildSavedLead(1L, 5L, LeadStatus.NEW);
        when(leadRepo.findById(1L)).thenReturn(Optional.of(lead));
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        LeadResponse resp = leadImpl.getLead(1L);

        assertThat(resp.getId()).isEqualTo(1L);
    }

    @Test
    void getLead_notFound_throwsRuntimeException() {
        when(leadRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadImpl.getLead(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lead");
    }

    // ─── listLeadsByOwner ─────────────────────────────────────────────────────────

    @Test
    void listLeadsByOwner_withDateRange_callsDateRangeMethod() {
        LocalDate from = LocalDate.now().minusDays(30);
        LocalDate to   = LocalDate.now();
        when(leadRepo.findByOwnerAndDateRange(5L, from, to)).thenReturn(List.of());

        List<LeadResponse> result = leadImpl.listLeadsByOwner(5L, null, from, to);

        assertThat(result).isEmpty();
        verify(leadRepo).findByOwnerAndDateRange(5L, from, to);
    }

    @Test
    void listLeadsByOwner_withStatus_callsStatusMethod() {
        when(leadRepo.findByOwnerIdAndStatus(5L, LeadStatus.NEW)).thenReturn(List.of());

        leadImpl.listLeadsByOwner(5L, LeadStatus.NEW, null, null);

        verify(leadRepo).findByOwnerIdAndStatus(5L, LeadStatus.NEW);
    }

    @Test
    void listLeadsByOwner_noFilters_callsFindByOwnerId() {
        when(leadRepo.findByOwnerId(5L)).thenReturn(List.of());

        leadImpl.listLeadsByOwner(5L, null, null, null);

        verify(leadRepo).findByOwnerId(5L);
    }

    // ─── assignLead / discardLead / updateLeadStatus ──────────────────────────────

    @Test
    void assignLead_updatesAssignedAgentAndStatus() {
        Lead lead = buildSavedLead(1L, 5L, LeadStatus.NEW);
        when(leadRepo.findById(1L)).thenReturn(Optional.of(lead));
        when(leadRepo.save(any())).thenReturn(lead);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));
        when(agentRepo.findById(10L)).thenReturn(Optional.of(buildAgentEntity(10L, 200L)));
        when(userRepo.findById(200L)).thenReturn(Optional.of(buildUserEntity(200L, "Agent")));

        leadImpl.assignLead(1L, 10L, 5L);

        verify(leadRepo).save(argThat(l ->
                Long.valueOf(10L).equals(l.getAssignedAgentId()) && l.getStatus() == LeadStatus.PENDING));
    }

    @Test
    void assignLead_notFound_throwsRuntimeException() {
        when(leadRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadImpl.assignLead(99L, 10L, 5L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void discardLead_setsStatusToDiscarded() {
        Lead lead = buildSavedLead(1L, 5L, LeadStatus.NEW);
        when(leadRepo.findById(1L)).thenReturn(Optional.of(lead));
        when(leadRepo.save(any())).thenReturn(lead);

        leadImpl.discardLead(1L, 5L);

        verify(leadRepo).save(argThat(l -> l.getStatus() == LeadStatus.DISCARDED));
    }

    @Test
    void discardLead_notFound_throwsRuntimeException() {
        when(leadRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadImpl.discardLead(99L, 5L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void updateLeadStatus_withCallbackDate_savesWithDate() {
        Lead lead = buildSavedLead(1L, 5L, LeadStatus.PENDING);
        LocalDate callbackDate = LocalDate.now().plusDays(3);
        when(leadRepo.findById(1L)).thenReturn(Optional.of(lead));
        when(leadRepo.save(any())).thenReturn(lead);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        leadImpl.updateLeadStatus(1L, LeadStatus.CALLBACK, callbackDate);

        verify(leadRepo).save(argThat(l ->
                l.getStatus() == LeadStatus.CALLBACK && callbackDate.equals(l.getCallbackDate())));
    }

    @Test
    void updateLeadStatus_toConverted_savesWithoutCallbackDate() {
        Lead lead = buildSavedLead(1L, 5L, LeadStatus.INTERESTED);
        when(leadRepo.findById(1L)).thenReturn(Optional.of(lead));
        when(leadRepo.save(any())).thenReturn(lead);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        leadImpl.updateLeadStatus(1L, LeadStatus.CONVERTED, null);

        verify(leadRepo).save(argThat(l -> l.getStatus() == LeadStatus.CONVERTED));
    }

    // ─── updateLead ──────────────────────────────────────────────────────────────

    @Test
    void updateLead_updatesFieldsAndSaves() {
        Lead lead = buildSavedLead(1L, 5L, LeadStatus.NEW);
        CreateLeadRequest req = buildRequest("Updated Name", "5559999");
        when(leadRepo.findById(1L)).thenReturn(Optional.of(lead));
        when(leadRepo.save(any())).thenReturn(lead);
        when(userRepo.findById(5L)).thenReturn(Optional.of(buildUserEntity(5L, "Owner")));

        leadImpl.updateLead(1L, req, 5L);

        verify(leadRepo).save(argThat(l -> "Updated Name".equals(l.getContactName())));
    }

    @Test
    void updateLead_notFound_throwsRuntimeException() {
        when(leadRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> leadImpl.updateLead(99L, buildRequest("N", "P"), 5L))
                .isInstanceOf(RuntimeException.class);
    }
}
