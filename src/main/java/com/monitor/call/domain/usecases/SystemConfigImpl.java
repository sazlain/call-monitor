package com.monitor.call.domain.usecases;

import com.monitor.call.domain.exceptions.NotFoundException;
import com.monitor.call.domain.models.SystemConfig;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.ports.out.SystemConfigRepositoryPort;
import com.monitor.call.domain.responses.SystemConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SystemConfigImpl implements SystemConfigUseCases {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigImpl.class);

    private final SystemConfigRepositoryPort repo;

    public SystemConfigImpl(SystemConfigRepositoryPort repo) {
        this.repo = repo;
    }

    @Override
    public List<SystemConfigResponse> listByAdmin(Long adminId) {
        return repo.findByAdminId(adminId).stream().map(this::toResponse).toList();
    }

    @Override
    public SystemConfigResponse getByKey(Long adminId, String key) {
        return repo.findByAdminIdAndKey(adminId, key)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Config no encontrada: " + key));
    }

    @Override
    public SystemConfigResponse upsert(Long adminId, String key, String value) {
        SystemConfig config = repo.findByAdminIdAndKey(adminId, key)
                .orElseGet(() -> buildDefault(adminId, key));
        config.setConfigValue(value);
        SystemConfig saved = repo.save(config);
        logger.info("Config actualizada: adminId={} key={} value={}", adminId, key, value);
        return toResponse(saved);
    }

    @Override
    public void seedDefaults(Long adminId) {
        for (ConfigSeed seed : ConfigSeed.values()) {
            if (!repo.existsByAdminIdAndKey(adminId, seed.key)) {
                repo.save(SystemConfig.builder()
                        .adminId(adminId)
                        .configKey(seed.key)
                        .defaultValue(seed.defaultValue)
                        .required(seed.required)
                        .description(seed.description)
                        .valueType(seed.valueType)
                        .build());
                logger.info("Config sembrada: adminId={} key={}", adminId, seed.key);
            }
        }
    }

    @Override
    public String getValue(Long adminId, String key) {
        return repo.findByAdminIdAndKey(adminId, key)
                .map(SystemConfig::effectiveValue)
                .orElse(ConfigSeed.defaultFor(key));
    }

    @Override
    public boolean getBooleanValue(Long adminId, String key) {
        return Boolean.parseBoolean(getValue(adminId, key));
    }

    @Override
    public int getIntValue(Long adminId, String key) {
        try {
            return Integer.parseInt(getValue(adminId, key));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SystemConfig buildDefault(Long adminId, String key) {
        ConfigSeed seed = ConfigSeed.forKey(key);
        return SystemConfig.builder()
                .adminId(adminId)
                .configKey(key)
                .defaultValue(seed != null ? seed.defaultValue : null)
                .required(seed != null && seed.required)
                .description(seed != null ? seed.description : null)
                .valueType(seed != null ? seed.valueType : "STRING")
                .build();
    }

    private SystemConfigResponse toResponse(SystemConfig c) {
        return SystemConfigResponse.builder()
                .id(c.getId())
                .configKey(c.getConfigKey())
                .configValue(c.getConfigValue())
                .defaultValue(c.getDefaultValue())
                .effectiveValue(c.effectiveValue())
                .required(c.getRequired())
                .description(c.getDescription())
                .valueType(c.getValueType())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    // ── Definición de configuraciones por defecto ─────────────────────────────

    public enum ConfigSeed {
        LEADS_VISIBILITY(
            "leads.visibility", "false", "BOOLEAN", false,
            "Leads públicos — todos los agentes los ven"),
        LEADS_ASSIGNMENT_MODE(
            "leads.assignment_mode", "MANUAL", "STRING", false,
            "Modo de asignación de leads: MANUAL o AUTO_ROUND_ROBIN"),
        AGENTS_SCHEDULE(
            "agents.schedule", "{\"type\":\"FREE\"}", "JSON", false,
            "Horario laboral de agentes por día de la semana"),
        ALERTS_IDLE_THRESHOLD(
            "alerts.idle_threshold_minutes", "30", "INTEGER", false,
            "Minutos de inactividad antes de generar alerta"),
        ALERTS_IDLE_ENABLED(
            "alerts.idle_enabled", "false", "BOOLEAN", false,
            "Enviar alerta de tiempo de inactividad al administrador"),
        ALERTS_APPOINTMENT_EMAIL(
            "alerts.appointment_email", "true", "BOOLEAN", false,
            "Notificar al administrador cuando se agenda una cita"),
        ALERTS_UNKNOWN_CALL_EMAIL(
            "alerts.unknown_call_email", "true", "BOOLEAN", false,
            "Alertar cuando se llama a un número no asociado a ningún lead"),
        ALERTS_DAILY_SUMMARY(
            "alerts.daily_summary", "true", "BOOLEAN", false,
            "Enviar resumen diario de actividad al administrador"),
        ALERTS_CALLBACK_REMINDER(
            "alerts.callback_reminder", "true", "BOOLEAN", false,
            "Recordatorio de callbacks vencidos (enviado a las 9 AM)"),
        ALERTS_GOAL_NOT_MET(
            "alerts.goal_not_met", "true", "BOOLEAN", false,
            "Alerta al cierre del día si una meta no fue cumplida"),
        ALERTS_LONG_CALL_THRESHOLD(
            "alerts.long_call_threshold_minutes", "15", "INTEGER", false,
            "Alertar cuando una llamada supera este número de minutos"),
        LEADS_AUTO_DISCARD_DAYS(
            "leads.auto_discard_days", "0", "INTEGER", false,
            "Auto-descartar leads sin actividad después de N días (0 = desactivado)"),
        LEADS_UNKNOWN_CREATE_LEAD(
            "leads.unknown.create_lead", "true", "BOOLEAN", false,
            "Mostrar modal para convertir números desconocidos en lead (false = solo alerta por email)"),
        LEADS_MAX_CALLBACKS(
            "leads.max_callbacks", "3", "INTEGER", false,
            "Número máximo de callbacks antes de descartar automáticamente un lead"),
        DASHBOARD_REFRESH_INTERVAL(
            "dashboard.refresh_interval_seconds", "30", "INTEGER", false,
            "Intervalo de refresco automático del dashboard en segundos");

        public final String key;
        public final String defaultValue;
        public final String valueType;
        public final boolean required;
        public final String description;

        ConfigSeed(String key, String defaultValue, String valueType,
                   boolean required, String description) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.valueType = valueType;
            this.required = required;
            this.description = description;
        }

        public static ConfigSeed forKey(String key) {
            for (ConfigSeed s : values()) if (s.key.equals(key)) return s;
            return null;
        }

        public static String defaultFor(String key) {
            ConfigSeed s = forKey(key);
            return s != null ? s.defaultValue : null;
        }
    }
}
