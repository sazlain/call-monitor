package com.monitor.call.infrastructure.persistence;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.models.License;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.impl.LicenseRepositoryImpl;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseRepositoryImplTest {

    @Mock private LicenseJpaRepository repo;

    @InjectMocks private LicenseRepositoryImpl licenseRepo;

    private LicenseEntity buildEntity(Long id, Long adminId, LicenseStatus status, int maxAgents) {
        return LicenseEntity.builder()
                .id(id).adminId(adminId).status(status).maxAgents(maxAgents)
                .planName("Basic").build();
    }

    // ─── findByAdminId ────────────────────────────────────────────────────────

    @Test
    void findByAdminId_found_returnsMappedLicense() {
        when(repo.findByAdminId(1L)).thenReturn(
                Optional.of(buildEntity(10L, 1L, LicenseStatus.ACTIVE, 5)));

        Optional<License> result = licenseRepo.findByAdminId(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(10L);
        assertThat(result.get().getAdminId()).isEqualTo(1L);
        assertThat(result.get().getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(result.get().getMaxAgents()).isEqualTo(5);
    }

    @Test
    void findByAdminId_notFound_returnsEmpty() {
        when(repo.findByAdminId(99L)).thenReturn(Optional.empty());

        assertThat(licenseRepo.findByAdminId(99L)).isEmpty();
    }

    @Test
    void findByAdminId_expired_mapsStatusCorrectly() {
        when(repo.findByAdminId(2L)).thenReturn(
                Optional.of(buildEntity(20L, 2L, LicenseStatus.EXPIRED, 3)));

        Optional<License> result = licenseRepo.findByAdminId(2L);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(LicenseStatus.EXPIRED);
    }

    @Test
    void findByAdminId_suspended_mapsStatusCorrectly() {
        when(repo.findByAdminId(3L)).thenReturn(
                Optional.of(buildEntity(30L, 3L, LicenseStatus.SUSPENDED, 10)));

        Optional<License> result = licenseRepo.findByAdminId(3L);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(LicenseStatus.SUSPENDED);
        assertThat(result.get().getMaxAgents()).isEqualTo(10);
    }
}
