package com.monitor.call.domain.usecases;

import com.monitor.call.domain.models.SystemConfig;
import com.monitor.call.domain.ports.out.SystemConfigRepositoryPort;
import com.monitor.call.domain.responses.SystemConfigResponse;
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
class SystemConfigImplTest {

    @Mock private SystemConfigRepositoryPort repo;

    @InjectMocks
    private SystemConfigImpl systemConfigImpl;

    // ─── helpers ─────────────────────────────────────────────────────────────

    private SystemConfig buildConfig(String key, String value, String defaultValue) {
        return SystemConfig.builder()
                .id(1L).adminId(1L).configKey(key)
                .configValue(value).defaultValue(defaultValue)
                .required(false).description("desc")
                .valueType("STRING").build();
    }

    // ─── listByAdmin ──────────────────────────────────────────────────────────

    @Test
    void listByAdmin_returnsAllConfigs() {
        when(repo.findByAdminId(1L)).thenReturn(List.of(
                buildConfig("leads.visibility", "true", "false"),
                buildConfig("alerts.idle_enabled", "false", "false")));

        List<SystemConfigResponse> result = systemConfigImpl.listByAdmin(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getConfigKey()).isEqualTo("leads.visibility");
    }

    @Test
    void listByAdmin_empty_returnsEmptyList() {
        when(repo.findByAdminId(2L)).thenReturn(List.of());

        assertThat(systemConfigImpl.listByAdmin(2L)).isEmpty();
    }

    // ─── getByKey ─────────────────────────────────────────────────────────────

    @Test
    void getByKey_found_returnsResponse() {
        SystemConfig c = buildConfig("leads.visibility", "true", "false");
        when(repo.findByAdminIdAndKey(1L, "leads.visibility")).thenReturn(Optional.of(c));

        SystemConfigResponse resp = systemConfigImpl.getByKey(1L, "leads.visibility");

        assertThat(resp.getConfigKey()).isEqualTo("leads.visibility");
        assertThat(resp.getEffectiveValue()).isEqualTo("true");
    }

    @Test
    void getByKey_notFound_throwsRuntimeException() {
        when(repo.findByAdminIdAndKey(1L, "unknown.key")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> systemConfigImpl.getByKey(1L, "unknown.key"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Config");
    }

    // ─── upsert ───────────────────────────────────────────────────────────────

    @Test
    void upsert_existingConfig_updatesValue() {
        SystemConfig existing = buildConfig("leads.visibility", "false", "false");
        SystemConfig updated = buildConfig("leads.visibility", "true", "false");
        when(repo.findByAdminIdAndKey(1L, "leads.visibility")).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenReturn(updated);

        SystemConfigResponse resp = systemConfigImpl.upsert(1L, "leads.visibility", "true");

        verify(repo).save(argThat(c -> "true".equals(c.getConfigValue())));
        assertThat(resp.getConfigKey()).isEqualTo("leads.visibility");
    }

    @Test
    void upsert_newConfig_createsWithSeedDefaults() {
        SystemConfig saved = buildConfig("leads.visibility", "true", "false");
        when(repo.findByAdminIdAndKey(1L, "leads.visibility")).thenReturn(Optional.empty());
        when(repo.save(any())).thenReturn(saved);

        SystemConfigResponse resp = systemConfigImpl.upsert(1L, "leads.visibility", "true");

        verify(repo).save(argThat(c -> "true".equals(c.getConfigValue())));
        assertThat(resp).isNotNull();
    }

    @Test
    void upsert_unknownKey_createsWithNullDefaults() {
        SystemConfig saved = buildConfig("custom.key", "value", null);
        when(repo.findByAdminIdAndKey(1L, "custom.key")).thenReturn(Optional.empty());
        when(repo.save(any())).thenReturn(saved);

        SystemConfigResponse resp = systemConfigImpl.upsert(1L, "custom.key", "value");

        verify(repo).save(any());
        assertThat(resp).isNotNull();
    }

    // ─── seedDefaults ─────────────────────────────────────────────────────────

    @Test
    void seedDefaults_keysNotExisting_savesAll() {
        when(repo.existsByAdminIdAndKey(eq(1L), anyString())).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        systemConfigImpl.seedDefaults(1L);

        int expectedSeeds = SystemConfigImpl.ConfigSeed.values().length;
        verify(repo, times(expectedSeeds)).save(any());
    }

    @Test
    void seedDefaults_someKeysExisting_savesOnlyMissing() {
        when(repo.existsByAdminIdAndKey(eq(1L), anyString())).thenReturn(true);

        systemConfigImpl.seedDefaults(1L);

        verify(repo, never()).save(any());
    }

    @Test
    void seedDefaults_partiallySeeded_savesOnlyNew() {
        String firstKey = SystemConfigImpl.ConfigSeed.values()[0].key;
        when(repo.existsByAdminIdAndKey(1L, firstKey)).thenReturn(true);
        when(repo.existsByAdminIdAndKey(eq(1L), argThat(k -> !k.equals(firstKey)))).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        systemConfigImpl.seedDefaults(1L);

        int expectedSaves = SystemConfigImpl.ConfigSeed.values().length - 1;
        verify(repo, times(expectedSaves)).save(any());
    }

    // ─── getValue ─────────────────────────────────────────────────────────────

    @Test
    void getValue_configExists_returnsEffectiveValue() {
        SystemConfig c = buildConfig("leads.visibility", "true", "false");
        when(repo.findByAdminIdAndKey(1L, "leads.visibility")).thenReturn(Optional.of(c));

        String value = systemConfigImpl.getValue(1L, "leads.visibility");

        assertThat(value).isEqualTo("true");
    }

    @Test
    void getValue_configNotFound_returnsDefaultFromSeed() {
        when(repo.findByAdminIdAndKey(1L, "leads.visibility")).thenReturn(Optional.empty());

        String value = systemConfigImpl.getValue(1L, "leads.visibility");

        assertThat(value).isEqualTo("false");
    }

    @Test
    void getValue_configNotFoundUnknownKey_returnsNull() {
        when(repo.findByAdminIdAndKey(1L, "unknown.key")).thenReturn(Optional.empty());

        String value = systemConfigImpl.getValue(1L, "unknown.key");

        assertThat(value).isNull();
    }

    @Test
    void getValue_configFoundWithNullValue_returnsDefaultValue() {
        SystemConfig c = buildConfig("leads.visibility", null, "false");
        when(repo.findByAdminIdAndKey(1L, "leads.visibility")).thenReturn(Optional.of(c));

        String value = systemConfigImpl.getValue(1L, "leads.visibility");

        assertThat(value).isEqualTo("false");
    }

    // ─── getBooleanValue ──────────────────────────────────────────────────────

    @Test
    void getBooleanValue_trueString_returnsTrue() {
        SystemConfig c = buildConfig("alerts.idle_enabled", "true", "false");
        when(repo.findByAdminIdAndKey(1L, "alerts.idle_enabled")).thenReturn(Optional.of(c));

        assertThat(systemConfigImpl.getBooleanValue(1L, "alerts.idle_enabled")).isTrue();
    }

    @Test
    void getBooleanValue_falseString_returnsFalse() {
        SystemConfig c = buildConfig("alerts.idle_enabled", "false", "true");
        when(repo.findByAdminIdAndKey(1L, "alerts.idle_enabled")).thenReturn(Optional.of(c));

        assertThat(systemConfigImpl.getBooleanValue(1L, "alerts.idle_enabled")).isFalse();
    }

    // ─── getIntValue ──────────────────────────────────────────────────────────

    @Test
    void getIntValue_validInteger_returnsParsedValue() {
        SystemConfig c = buildConfig("alerts.idle_threshold_minutes", "30", "30");
        when(repo.findByAdminIdAndKey(1L, "alerts.idle_threshold_minutes")).thenReturn(Optional.of(c));

        assertThat(systemConfigImpl.getIntValue(1L, "alerts.idle_threshold_minutes")).isEqualTo(30);
    }

    @Test
    void getIntValue_invalidInteger_returnsZero() {
        SystemConfig c = buildConfig("some.key", "not-a-number", null);
        when(repo.findByAdminIdAndKey(1L, "some.key")).thenReturn(Optional.of(c));

        assertThat(systemConfigImpl.getIntValue(1L, "some.key")).isEqualTo(0);
    }

    // ─── ConfigSeed ───────────────────────────────────────────────────────────

    @Test
    void configSeed_forKey_returnsMatchingSeed() {
        SystemConfigImpl.ConfigSeed seed = SystemConfigImpl.ConfigSeed.forKey("leads.visibility");
        assertThat(seed).isEqualTo(SystemConfigImpl.ConfigSeed.LEADS_VISIBILITY);
    }

    @Test
    void configSeed_forKey_unknownKey_returnsNull() {
        assertThat(SystemConfigImpl.ConfigSeed.forKey("unknown")).isNull();
    }

    @Test
    void configSeed_defaultFor_knownKey_returnsDefaultValue() {
        String def = SystemConfigImpl.ConfigSeed.defaultFor("leads.visibility");
        assertThat(def).isEqualTo("false");
    }

    @Test
    void configSeed_defaultFor_unknownKey_returnsNull() {
        assertThat(SystemConfigImpl.ConfigSeed.defaultFor("unknown")).isNull();
    }
}
