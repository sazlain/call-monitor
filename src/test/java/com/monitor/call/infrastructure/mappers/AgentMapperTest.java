package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.models.Agent;
import com.monitor.call.domain.models.AgentGroup;
import com.monitor.call.domain.responses.AgentGroupResponse;
import com.monitor.call.domain.responses.AgentResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGroupEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMapperTest {

    // ─── entityToDomain ───────────────────────────────────────────────────────

    @Test
    void entityToDomain_withoutGroup_mapsFieldsCorrectly() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AgentEntity entity = AgentEntity.builder()
                .id(1L).userId(10L).extension("1001").active(true)
                .createdAt(now).updatedAt(now)
                .build();

        Agent domain = AgentMapper.entityToDomain(entity);

        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getUserId()).isEqualTo(10L);
        assertThat(domain.getExtension()).isEqualTo("1001");
        assertThat(domain.getActive()).isTrue();
        assertThat(domain.getGroupId()).isNull();
        assertThat(domain.getGroupName()).isNull();
        assertThat(domain.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void entityToDomain_withGroup_mapsGroupFields() {
        AgentGroupEntity groupEntity = AgentGroupEntity.builder()
                .id(5L).name("Sales").adminId(1L).active(true).build();
        AgentEntity entity = AgentEntity.builder()
                .id(2L).userId(20L).extension("1002").active(true)
                .group(groupEntity)
                .build();

        Agent domain = AgentMapper.entityToDomain(entity);

        assertThat(domain.getGroupId()).isEqualTo(5L);
        assertThat(domain.getGroupName()).isEqualTo("Sales");
    }

    // ─── domainToEntity ───────────────────────────────────────────────────────

    @Test
    void domainToEntity_mapsBasicFields() {
        Agent domain = Agent.builder()
                .id(1L).userId(10L).extension("1001").active(true).build();

        AgentEntity entity = AgentMapper.domainToEntity(domain);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo(10L);
        assertThat(entity.getExtension()).isEqualTo("1001");
        assertThat(entity.getActive()).isTrue();
    }

    @Test
    void domainToEntity_nullActive_defaultsToTrue() {
        Agent domain = Agent.builder().id(1L).userId(10L).extension("1001").active(null).build();

        AgentEntity entity = AgentMapper.domainToEntity(domain);

        assertThat(entity.getActive()).isTrue();
    }

    // ─── domainToResponse ────────────────────────────────────────────────────

    @Test
    void domainToResponse_withUser_mapsNameAndEmail() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Agent domain = Agent.builder()
                .id(1L).userId(10L).extension("1001").active(true)
                .groupId(5L).groupName("Sales")
                .createdAt(now)
                .build();
        UserEntity user = UserEntity.builder().id(10L).name("Ana").email("ana@test.com")
                .password("hash").active(true).build();

        AgentResponse resp = AgentMapper.domainToResponse(domain, Map.of(10L, user));

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getExtension()).isEqualTo("1001");
        assertThat(resp.getName()).isEqualTo("Ana");
        assertThat(resp.getEmail()).isEqualTo("ana@test.com");
        assertThat(resp.getGroupId()).isEqualTo(5L);
        assertThat(resp.getGroupName()).isEqualTo("Sales");
        assertThat(resp.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void domainToResponse_userNotInMap_usesDefaults() {
        Agent domain = Agent.builder()
                .id(1L).userId(10L).extension("1001").active(true).build();

        AgentResponse resp = AgentMapper.domainToResponse(domain, Map.of());

        assertThat(resp.getName()).isEqualTo("Desconocido");
        assertThat(resp.getEmail()).isEmpty();
    }

    // ─── groupEntityToResponse ────────────────────────────────────────────────

    @Test
    void groupEntityToResponse_withAgents_mapsAllFields() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AgentGroupEntity groupEntity = AgentGroupEntity.builder()
                .id(5L).name("Sales").description("Sales team")
                .adminId(1L).active(true).createdAt(now).build();

        UserEntity user = UserEntity.builder().id(10L).name("Ana").email("ana@test.com")
                .password("hash").active(true).build();
        Agent agent = Agent.builder().id(1L).userId(10L).extension("1001").active(true).build();
        AgentResponse agentResp = AgentMapper.domainToResponse(agent, Map.of(10L, user));

        AgentGroupResponse resp = AgentMapper.groupEntityToResponse(groupEntity, List.of(agentResp));

        assertThat(resp.getId()).isEqualTo(5L);
        assertThat(resp.getName()).isEqualTo("Sales");
        assertThat(resp.getDescription()).isEqualTo("Sales team");
        assertThat(resp.getAdminId()).isEqualTo(1L);
        assertThat(resp.getActive()).isTrue();
        assertThat(resp.getAgentCount()).isEqualTo(1);
        assertThat(resp.getAgents()).hasSize(1);
        assertThat(resp.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void groupEntityToResponse_nullAgents_usesEmptyList() {
        AgentGroupEntity groupEntity = AgentGroupEntity.builder()
                .id(5L).name("Empty").adminId(1L).active(true).build();

        AgentGroupResponse resp = AgentMapper.groupEntityToResponse(groupEntity, null);

        assertThat(resp.getAgentCount()).isZero();
        assertThat(resp.getAgents()).isEmpty();
    }
}
