package com.monitor.call.infrastructure.mappers;

import com.monitor.call.domain.enums.Role;
import com.monitor.call.domain.models.User;
import com.monitor.call.domain.responses.UserResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.UserEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    // ─── entityToDomain ───────────────────────────────────────────────────────

    @Test
    void entityToDomain_mapsAllFields() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UserEntity entity = UserEntity.builder()
                .id(1L).name("Ana").email("ana@test.com").password("hashed")
                .active(true).roles(Set.of(Role.ADMIN)).mustChangePassword(false)
                .createdAt(now).updatedAt(now)
                .build();

        User domain = UserMapper.entityToDomain(entity);

        assertThat(domain.getId()).isEqualTo(1L);
        assertThat(domain.getName()).isEqualTo("Ana");
        assertThat(domain.getEmail()).isEqualTo("ana@test.com");
        assertThat(domain.getPassword()).isEqualTo("hashed");
        assertThat(domain.getActive()).isTrue();
        assertThat(domain.getRoles()).contains(Role.ADMIN);
        assertThat(domain.getMustChangePassword()).isFalse();
        assertThat(domain.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void entityToDomain_withMultipleRoles_mapsAllRoles() {
        UserEntity entity = UserEntity.builder()
                .id(2L).name("Bob").email("bob@test.com").password("hash")
                .active(true).roles(Set.of(Role.ADMIN, Role.SALES_AGENT))
                .mustChangePassword(true)
                .build();

        User domain = UserMapper.entityToDomain(entity);

        assertThat(domain.getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.SALES_AGENT);
        assertThat(domain.getMustChangePassword()).isTrue();
    }

    // ─── domainToEntity ───────────────────────────────────────────────────────

    @Test
    void domainToEntity_mapsAllFields() {
        User domain = User.builder()
                .id(1L).name("Ana").email("ana@test.com").password("hashed")
                .active(true).roles(Set.of(Role.CALL_AGENT)).mustChangePassword(true)
                .build();

        UserEntity entity = UserMapper.domainToEntity(domain);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getName()).isEqualTo("Ana");
        assertThat(entity.getEmail()).isEqualTo("ana@test.com");
        assertThat(entity.getPassword()).isEqualTo("hashed");
        assertThat(entity.getActive()).isTrue();
        assertThat(entity.getRoles()).contains(Role.CALL_AGENT);
        assertThat(entity.getMustChangePassword()).isTrue();
    }

    // ─── domainToResponse ────────────────────────────────────────────────────

    @Test
    void domainToResponse_mapsAllFields() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        User domain = User.builder()
                .id(1L).name("Ana").email("ana@test.com")
                .active(true).roles(Set.of(Role.ADMIN))
                .mustChangePassword(false).createdAt(now)
                .build();

        UserResponse resp = UserMapper.domainToResponse(domain);

        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getName()).isEqualTo("Ana");
        assertThat(resp.getEmail()).isEqualTo("ana@test.com");
        assertThat(resp.getActive()).isTrue();
        assertThat(resp.getRoles()).contains(Role.ADMIN);
        assertThat(resp.getMustChangePassword()).isFalse();
        assertThat(resp.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void domainToResponse_withCallAgentRole_mapsCorrectly() {
        User domain = User.builder()
                .id(2L).name("Bob").email("bob@test.com")
                .active(false).roles(Set.of(Role.CALL_AGENT))
                .mustChangePassword(true)
                .build();

        UserResponse resp = UserMapper.domainToResponse(domain);

        assertThat(resp.getActive()).isFalse();
        assertThat(resp.getRoles()).contains(Role.CALL_AGENT);
        assertThat(resp.getMustChangePassword()).isTrue();
    }
}
