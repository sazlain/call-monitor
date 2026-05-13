package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.enums.CallResult;
import com.monitor.call.domain.enums.LeadStatus;
import com.monitor.call.domain.models.CallTypification;
import com.monitor.call.domain.models.Lead;
import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.domain.responses.LeadResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallTypificationEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LeadEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LeadMapperTest {

    // ─── entityToDomain ───────────────────────────────────────────────────────

    @Test
    void entityToDomain_mapsAllFields() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LeadEntity entity = LeadEntity.builder()
                .id(1L).contactName("Juan").contactPhone("5551001")
                .leadSource("WEB").notes("note").leadDate(LocalDate.now())
                .ownerId(5L).assignedAgentId(10L)
                .status(LeadStatus.NEW).callbackDate(null)
                .createdAt(now).updatedAt(now)
                .build();

        Lead domain = LeadMapper.entityToDomain(entity);

        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getContactName()).isEqualTo("Juan");
        assertThat(domain.getContactPhone()).isEqualTo("5551001");
        assertThat(domain.getLeadSource()).isEqualTo("WEB");
        assertThat(domain.getNotes()).isEqualTo("note");
        assertThat(domain.getOwnerId()).isEqualTo(5L);
        assertThat(domain.getAssignedAgentId()).isEqualTo(10L);
        assertThat(domain.getStatus()).isEqualTo(LeadStatus.NEW);
        assertThat(domain.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void entityToDomain_withCallbackDate_mapsCallbackDate() {
        LocalDate cb = LocalDate.now().plusDays(3);
        LeadEntity entity = LeadEntity.builder()
                .id(2L).contactName("Ana").contactPhone("5552002")
                .leadSource("REF").leadDate(LocalDate.now())
                .ownerId(5L).status(LeadStatus.CALLBACK)
                .callbackDate(cb).build();

        Lead domain = LeadMapper.entityToDomain(entity);

        assertThat(domain.getCallbackDate()).isEqualTo(cb);
        assertThat(domain.getStatus()).isEqualTo(LeadStatus.CALLBACK);
    }

    // ─── domainToEntity ───────────────────────────────────────────────────────

    @Test
    void domainToEntity_mapsAllFields() {
        Lead domain = Lead.builder()
                .id(1L).contactName("Pedro").contactPhone("5553003")
                .leadSource("PHONE").notes("test").leadDate(LocalDate.now())
                .ownerId(5L).assignedAgentId(20L)
                .status(LeadStatus.PENDING).build();

        LeadEntity entity = LeadMapper.domainToEntity(domain);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getContactName()).isEqualTo("Pedro");
        assertThat(entity.getContactPhone()).isEqualTo("5553003");
        assertThat(entity.getLeadSource()).isEqualTo("PHONE");
        assertThat(entity.getOwnerId()).isEqualTo(5L);
        assertThat(entity.getAssignedAgentId()).isEqualTo(20L);
        assertThat(entity.getStatus()).isEqualTo(LeadStatus.PENDING);
    }

    // ─── domainToResponse ────────────────────────────────────────────────────

    @Test
    void domainToResponse_mapsFieldsAndNames() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Lead domain = Lead.builder()
                .id(1L).contactName("Juan").contactPhone("5551001")
                .leadSource("WEB").notes("note").leadDate(LocalDate.now())
                .ownerId(5L).assignedAgentId(10L)
                .status(LeadStatus.INTERESTED)
                .createdAt(now).updatedAt(now)
                .build();

        LeadResponse resp = LeadMapper.domainToResponse(domain, "Owner Name", "Agent Name");

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getContactName()).isEqualTo("Juan");
        assertThat(resp.getOwnerName()).isEqualTo("Owner Name");
        assertThat(resp.getAssignedAgentName()).isEqualTo("Agent Name");
        assertThat(resp.getStatus()).isEqualTo(LeadStatus.INTERESTED);
        assertThat(resp.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void domainToResponse_nullNames_mapsAsNull() {
        Lead domain = Lead.builder()
                .id(2L).contactName("X").contactPhone("Y").leadSource("Z")
                .leadDate(LocalDate.now()).ownerId(1L).status(LeadStatus.NEW).build();

        LeadResponse resp = LeadMapper.domainToResponse(domain, null, null);

        assertThat(resp.getOwnerName()).isNull();
        assertThat(resp.getAssignedAgentName()).isNull();
    }

    // ─── typEntityToDomain ────────────────────────────────────────────────────

    @Test
    void typEntityToDomain_mapsAllFields() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CallTypificationEntity entity = CallTypificationEntity.builder()
                .id(1L).callId("CALL-001").agentId(10L).leadId(5L)
                .result(CallResult.SALE).contactName("Contact").contactPhone("555")
                .notes("note").callbackDate(null)
                .createdAt(now).updatedAt(now)
                .build();

        CallTypification domain = LeadMapper.typEntityToDomain(entity);

        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getCallId()).isEqualTo("CALL-001");
        assertThat(domain.getAgentId()).isEqualTo(10L);
        assertThat(domain.getLeadId()).isEqualTo(5L);
        assertThat(domain.getResult()).isEqualTo(CallResult.SALE);
        assertThat(domain.getContactName()).isEqualTo("Contact");
        assertThat(domain.getCreatedAt()).isEqualTo(now);
    }

    // ─── typDomainToEntity ────────────────────────────────────────────────────

    @Test
    void typDomainToEntity_mapsAllFields() {
        CallTypification domain = CallTypification.builder()
                .id(1L).callId("CALL-002").agentId(10L).leadId(5L)
                .result(CallResult.CALLBACK).contactName("Test").contactPhone("555")
                .notes("cb note").callbackDate(LocalDate.now().plusDays(2))
                .build();

        CallTypificationEntity entity = LeadMapper.typDomainToEntity(domain);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getCallId()).isEqualTo("CALL-002");
        assertThat(entity.getResult()).isEqualTo(CallResult.CALLBACK);
        assertThat(entity.getCallbackDate()).isEqualTo(LocalDate.now().plusDays(2));
    }

    // ─── typDomainToResponse ─────────────────────────────────────────────────

    @Test
    void typDomainToResponse_mapsFieldsAndAgentName() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CallTypification domain = CallTypification.builder()
                .id(1L).callId("CALL-003").agentId(10L).leadId(5L)
                .result(CallResult.INTERESTED).contactName("Contact").contactPhone("555")
                .notes("note").createdAt(now)
                .build();

        CallTypificationResponse resp = LeadMapper.typDomainToResponse(domain, "Agent Ana");

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getCallId()).isEqualTo("CALL-003");
        assertThat(resp.getAgentName()).isEqualTo("Agent Ana");
        assertThat(resp.getResult()).isEqualTo(CallResult.INTERESTED);
        assertThat(resp.getCreatedAt()).isEqualTo(now);
    }
}
