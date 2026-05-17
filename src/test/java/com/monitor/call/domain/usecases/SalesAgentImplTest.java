package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.exceptions.BusinessRuleException;
import com.monitor.call.domain.responses.SalesAgentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.AgentJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LeadJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.services.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesAgentImplTest {

    @Mock private UserJpaRepository    userRepo;
    @Mock private AgentJpaRepository   agentRepo;
    @Mock private LeadJpaRepository    leadRepo;
    @Mock private LicenseJpaRepository licenseRepo;
    @Mock private PasswordEncoder      passwordEncoder;
    @Mock private EmailService         emailService;

    @InjectMocks
    private SalesAgentImpl salesAgentImpl;

    // ── helpers ───────────────────────────────────────────────────────────────

    private UserEntity buildUser(Long id, String name, String email) {
        return UserEntity.builder()
                .id(id)
                .name(name)
                .email(email)
                .password("encoded")
                .roles(Set.of(Role.SALES_AGENT))
                .active(true)
                .adminId(10L)
                .defaultCallAgentId(null)
                .build();
    }

    private UserEntity buildUserWithAgent(Long id, String name, String email, Long defaultCallAgentId) {
        return UserEntity.builder()
                .id(id)
                .name(name)
                .email(email)
                .password("encoded")
                .roles(Set.of(Role.SALES_AGENT))
                .active(true)
                .adminId(10L)
                .defaultCallAgentId(defaultCallAgentId)
                .build();
    }

    private LicenseEntity buildLicense(Integer maxSalesAgents) {
        return LicenseEntity.builder()
                .id(1L)
                .adminId(10L)
                .planName("Test Plan")
                .maxAgents(5)
                .maxSalesAgents(maxSalesAgents)
                .build();
    }

    private AgentEntity buildAgent(Long id, Long userId) {
        return AgentEntity.builder()
                .id(id)
                .userId(userId)
                .extension("100")
                .active(true)
                .build();
    }

    /** Stubs userRepo.save() to return the argument entity as-is. */
    private void stubSave() {
        when(userRepo.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Stubs leadRepo.findByOwnerId() to return empty list for any id. */
    private void stubNoLeads() {
        when(leadRepo.findByOwnerId(any())).thenReturn(List.of());
    }

    /** Stubs leadRepo.findByOwnerId() to return one lead for any id. */
    private void stubOneLeadForOwner(Long ownerId) {
        when(leadRepo.findByOwnerId(ownerId)).thenReturn(List.of(mock(LeadEntity.class)));
    }

    // ── createSalesAgent ─────────────────────────────────────────────────────

    @Test
    void createSalesAgent_duplicateEmail_throwsException() {
        when(userRepo.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> salesAgentImpl.createSalesAgent("Jane", "dup@test.com", null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("dup@test.com");

        verify(userRepo, never()).save(any());
    }

    @Test
    void createSalesAgent_noAdminId_createsSuccessfully() {
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        stubSave();
        stubNoLeads();

        SalesAgentResponse response = salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, null);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Jane");
        assertThat(response.getEmail()).isEqualTo("jane@test.com");
        assertThat(response.getAdminId()).isNull();
        verify(licenseRepo, never()).findByAdminId(any());
    }

    @Test
    void createSalesAgent_noLicense_createsSuccessfully() {
        Long adminId = 10L;
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        stubSave();
        stubNoLeads();

        SalesAgentResponse response = salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, adminId);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Jane");
        // No license found → no limit check, agent is created
        verify(userRepo).save(any());
    }

    @Test
    void createSalesAgent_moduleNotIncluded_throwsException() {
        Long adminId = 10L;
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        // maxSalesAgents = 0 means module not included
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.of(buildLicense(0)));

        assertThatThrownBy(() -> salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SALES_AGENT_NOT_INCLUDED");

        verify(userRepo, never()).save(any());
    }

    @Test
    void createSalesAgent_moduleNotIncluded_nullMaxSalesAgents_throwsException() {
        Long adminId = 10L;
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        // maxSalesAgents = null → treated as 0
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.of(buildLicense(null)));

        assertThatThrownBy(() -> salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SALES_AGENT_NOT_INCLUDED");

        verify(userRepo, never()).save(any());
    }

    @Test
    void createSalesAgent_limitReached_throwsException() {
        Long adminId = 10L;
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.of(buildLicense(2)));
        // Already 2 agents — at the limit
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId))
                .thenReturn(List.of(buildUser(1L, "A", "a@t.com"), buildUser(2L, "B", "b@t.com")));

        assertThatThrownBy(() -> salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SALES_AGENT_LIMIT_REACHED");

        verify(userRepo, never()).save(any());
    }

    @Test
    void createSalesAgent_withinLimit_createsSuccessfully() {
        Long adminId = 10L;
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.of(buildLicense(3)));
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId))
                .thenReturn(List.of(buildUser(1L, "A", "a@t.com")));  // 1 < 3
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        stubSave();
        stubNoLeads();

        SalesAgentResponse response = salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, adminId);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("jane@test.com");
        verify(userRepo).save(any());
    }

    @Test
    void createSalesAgent_limitReachedExactlyOne_messageHasNoPlural() {
        // maxAllowed == 1 → singular "Sales Agent" (no trailing 's')
        Long adminId = 10L;
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.of(buildLicense(1)));
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId))
                .thenReturn(List.of(buildUser(1L, "A", "a@t.com")));  // 1 >= 1

        assertThatThrownBy(() -> salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, adminId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SALES_AGENT_LIMIT_REACHED")
                .hasMessageContaining("1 Sales Agent");
    }

    @Test
    void createSalesAgent_emailFails_stillCreates() {
        Long adminId = 10L;
        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        stubSave();
        stubNoLeads();
        doThrow(new RuntimeException("SMTP error")).when(emailService).send(anyString(), anyString(), anyString());

        // Exception from emailService must NOT propagate
        SalesAgentResponse response = salesAgentImpl.createSalesAgent("Jane", "jane@test.com", null, adminId);

        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("jane@test.com");
        verify(userRepo).save(any());
    }

    @Test
    void createSalesAgent_withDefaultCallAgent_lookupsAgentName() {
        Long adminId    = 10L;
        Long agentId    = 20L;
        Long agentUserId = 30L;
        String agentUserName = "Call Agent Bob";

        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        stubSave();
        stubNoLeads();

        AgentEntity agentEntity = buildAgent(agentId, agentUserId);
        UserEntity  agentUser   = buildUser(agentUserId, agentUserName, "bob@test.com");
        when(agentRepo.findById(agentId)).thenReturn(Optional.of(agentEntity));
        when(userRepo.findById(agentUserId)).thenReturn(Optional.of(agentUser));

        // createSalesAgent looks up agent name for the email body (does NOT set on response)
        SalesAgentResponse response = salesAgentImpl.createSalesAgent("Jane", "jane@test.com", agentId, adminId);

        assertThat(response).isNotNull();
        // agentRepo.findById is called once in the email block and once in toResponse
        verify(agentRepo, times(2)).findById(agentId);
        verify(userRepo, atLeastOnce()).findById(agentUserId);
    }

    @Test
    void createSalesAgent_withDefaultCallAgent_agentNotFound_emailStillSent() {
        Long adminId = 10L;
        Long agentId = 99L;

        when(userRepo.existsByEmail("jane@test.com")).thenReturn(false);
        when(licenseRepo.findByAdminId(adminId)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        stubSave();
        stubNoLeads();

        when(agentRepo.findById(agentId)).thenReturn(Optional.empty());

        SalesAgentResponse response = salesAgentImpl.createSalesAgent("Jane", "jane@test.com", agentId, adminId);

        assertThat(response).isNotNull();
        // agentRepo.findById is called once in the email block and once in toResponse
        verify(agentRepo, times(2)).findById(agentId);
        verify(emailService).send(anyString(), anyString(), anyString());
    }

    // ── listSalesAgents ───────────────────────────────────────────────────────

    @Test
    void listSalesAgents_returnsAll() {
        Long adminId = 10L;
        UserEntity u1 = buildUser(1L, "Alice", "alice@test.com");
        UserEntity u2 = buildUser(2L, "Bob",   "bob@test.com");
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId)).thenReturn(List.of(u1, u2));
        when(leadRepo.findByOwnerId(1L)).thenReturn(List.of());
        when(leadRepo.findByOwnerId(2L)).thenReturn(List.of());

        List<SalesAgentResponse> result = salesAgentImpl.listSalesAgents(adminId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(SalesAgentResponse::getName)
                .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void listSalesAgents_emptyList_returnsEmpty() {
        Long adminId = 10L;
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId)).thenReturn(List.of());

        List<SalesAgentResponse> result = salesAgentImpl.listSalesAgents(adminId);

        assertThat(result).isEmpty();
    }

    // ── assignCallAgent ───────────────────────────────────────────────────────

    @Test
    void assignCallAgent_found_updatesAndReturns() {
        Long salesAgentId = 5L;
        Long callAgentId  = 20L;
        UserEntity user = buildUser(salesAgentId, "Alice", "alice@test.com");

        when(userRepo.findById(salesAgentId)).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        stubNoLeads();

        // toResponse will try to look up the call agent
        AgentEntity agentEntity = buildAgent(callAgentId, 99L);
        UserEntity  agentUser   = buildUser(99L, "Bob", "bob@test.com");
        when(agentRepo.findById(callAgentId)).thenReturn(Optional.of(agentEntity));
        when(userRepo.findById(99L)).thenReturn(Optional.of(agentUser));

        SalesAgentResponse response = salesAgentImpl.assignCallAgent(salesAgentId, callAgentId);

        assertThat(response).isNotNull();
        assertThat(response.getDefaultCallAgentId()).isEqualTo(callAgentId);
        assertThat(response.getDefaultCallAgentName()).isEqualTo("Bob");
        verify(userRepo).save(user);
    }

    @Test
    void assignCallAgent_notFound_throwsException() {
        Long salesAgentId = 999L;
        when(userRepo.findById(salesAgentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesAgentImpl.assignCallAgent(salesAgentId, 20L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");

        verify(userRepo, never()).save(any());
    }

    // ── deactivate ────────────────────────────────────────────────────────────

    @Test
    void deactivate_found_setsInactive() {
        Long salesAgentId = 5L;
        UserEntity user = buildUser(salesAgentId, "Alice", "alice@test.com");

        when(userRepo.findById(salesAgentId)).thenReturn(Optional.of(user));
        when(userRepo.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        salesAgentImpl.deactivate(salesAgentId);

        assertThat(user.getActive()).isFalse();
        verify(userRepo).save(user);
    }

    @Test
    void deactivate_notFound_throwsException() {
        Long salesAgentId = 999L;
        when(userRepo.findById(salesAgentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> salesAgentImpl.deactivate(salesAgentId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("999");

        verify(userRepo, never()).save(any());
    }

    // ── toResponse (via listSalesAgents) ──────────────────────────────────────

    @Test
    void toResponse_withNoDefaultAgent_leadCountZero() {
        Long adminId = 10L;
        UserEntity user = buildUser(1L, "Alice", "alice@test.com");  // defaultCallAgentId = null
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId)).thenReturn(List.of(user));
        when(leadRepo.findByOwnerId(1L)).thenReturn(List.of());

        List<SalesAgentResponse> result = salesAgentImpl.listSalesAgents(adminId);

        assertThat(result).hasSize(1);
        SalesAgentResponse r = result.get(0);
        assertThat(r.getDefaultCallAgentId()).isNull();
        assertThat(r.getDefaultCallAgentName()).isNull();
        assertThat(r.getLeadCount()).isZero();
        // agentRepo must NOT be called when defaultCallAgentId is null
        verify(agentRepo, never()).findById(any());
    }

    @Test
    void toResponse_withLeads_returnsCorrectCount() {
        Long adminId = 10L;
        UserEntity user = buildUser(1L, "Alice", "alice@test.com");
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId)).thenReturn(List.of(user));
        stubOneLeadForOwner(1L);

        List<SalesAgentResponse> result = salesAgentImpl.listSalesAgents(adminId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLeadCount()).isEqualTo(1);
    }

    @Test
    void toResponse_withDefaultCallAgent_agentFoundNoUser_nameNull() {
        Long adminId    = 10L;
        Long agentId    = 20L;
        Long agentUserId = 30L;

        UserEntity user = buildUserWithAgent(1L, "Alice", "alice@test.com", agentId);
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId)).thenReturn(List.of(user));
        when(leadRepo.findByOwnerId(1L)).thenReturn(List.of());

        AgentEntity agentEntity = buildAgent(agentId, agentUserId);
        when(agentRepo.findById(agentId)).thenReturn(Optional.of(agentEntity));
        // The user linked to this agent does NOT exist
        when(userRepo.findById(agentUserId)).thenReturn(Optional.empty());

        List<SalesAgentResponse> result = salesAgentImpl.listSalesAgents(adminId);

        assertThat(result).hasSize(1);
        SalesAgentResponse r = result.get(0);
        assertThat(r.getDefaultCallAgentId()).isEqualTo(agentId);
        assertThat(r.getDefaultCallAgentName()).isNull();
    }

    @Test
    void toResponse_withDefaultCallAgent_agentNotFound_nameNull() {
        Long adminId = 10L;
        Long agentId = 99L;

        UserEntity user = buildUserWithAgent(1L, "Alice", "alice@test.com", agentId);
        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId)).thenReturn(List.of(user));
        when(leadRepo.findByOwnerId(1L)).thenReturn(List.of());
        when(agentRepo.findById(agentId)).thenReturn(Optional.empty());

        List<SalesAgentResponse> result = salesAgentImpl.listSalesAgents(adminId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDefaultCallAgentName()).isNull();
    }

    @Test
    void toResponse_allFieldsMappedCorrectly() {
        Long adminId   = 10L;
        Long agentId   = 20L;
        Long agentUserId = 30L;

        UserEntity user = buildUserWithAgent(1L, "Alice", "alice@test.com", agentId);
        user.setAdminId(adminId);

        when(userRepo.findByRoleAndAdminId(Role.SALES_AGENT, adminId)).thenReturn(List.of(user));
        when(leadRepo.findByOwnerId(1L)).thenReturn(List.of(mock(LeadEntity.class), mock(LeadEntity.class)));

        AgentEntity agentEntity = buildAgent(agentId, agentUserId);
        UserEntity  agentUser   = buildUser(agentUserId, "Call Agent Carlos", "carlos@test.com");
        when(agentRepo.findById(agentId)).thenReturn(Optional.of(agentEntity));
        when(userRepo.findById(agentUserId)).thenReturn(Optional.of(agentUser));

        List<SalesAgentResponse> result = salesAgentImpl.listSalesAgents(adminId);

        assertThat(result).hasSize(1);
        SalesAgentResponse r = result.get(0);
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getName()).isEqualTo("Alice");
        assertThat(r.getEmail()).isEqualTo("alice@test.com");
        assertThat(r.getActive()).isTrue();
        assertThat(r.getAdminId()).isEqualTo(adminId);
        assertThat(r.getDefaultCallAgentId()).isEqualTo(agentId);
        assertThat(r.getDefaultCallAgentName()).isEqualTo("Call Agent Carlos");
        assertThat(r.getLeadCount()).isEqualTo(2);
    }
}
