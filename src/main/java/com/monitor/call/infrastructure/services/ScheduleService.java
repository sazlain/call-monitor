package com.monitor.call.infrastructure.services;

import com.monitor.call.domain.ports.in.ScheduleUseCases;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Interpreta la config agents.schedule y responde si el momento actual está
 * dentro del horario laboral esperado para un admin dado.
 *
 * Tipos soportados:
 *   FREE          → siempre dentro de horario
 *   FIXED         → días y tramos horarios específicos
 *   HOURS_PER_DAY → ventana horaria diaria (sin restricción de día)
 */
@Service
public class ScheduleService implements ScheduleUseCases {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private static final Map<DayOfWeek, String> DOW_KEY = Map.of(
            DayOfWeek.MONDAY,    "MON",
            DayOfWeek.TUESDAY,   "TUE",
            DayOfWeek.WEDNESDAY, "WED",
            DayOfWeek.THURSDAY,  "THU",
            DayOfWeek.FRIDAY,    "FRI",
            DayOfWeek.SATURDAY,  "SAT",
            DayOfWeek.SUNDAY,    "SUN"
    );

    private final SystemConfigUseCases configUseCases;
    private final ObjectMapper mapper;

    public ScheduleService(SystemConfigUseCases configUseCases, ObjectMapper mapper) {
        this.configUseCases = configUseCases;
        this.mapper = mapper;
    }

    /**
     * Retorna true si el instante dado cae dentro del horario laboral configurado.
     * Cuando hay error de parsing devuelve true para no bloquear ninguna alerta.
     */
    public boolean isWithinSchedule(Long adminId, OffsetDateTime now) {
        String raw = configUseCases.getValue(adminId, "agents.schedule");
        if (raw == null || raw.isBlank()) return true;
        try {
            JsonNode node = mapper.readTree(raw);
            return switch (node.path("type").asText("FREE")) {
                case "FIXED"         -> isWithinFixed(node, now);
                case "HOURS_PER_DAY" -> isWithinWindow(node, now.toLocalTime());
                default              -> true;
            };
        } catch (Exception e) {
            log.warn("Error al parsear agents.schedule adminId={}: {}", adminId, e.getMessage());
            return true;
        }
    }

    /**
     * Retorna true si el día de semana dado es un día laboral según la config.
     * FREE y HOURS_PER_DAY no restringen días → siempre true.
     * FIXED → solo los días que tengan entrada en days{}.
     */
    public boolean isWorkDay(Long adminId, DayOfWeek day) {
        String raw = configUseCases.getValue(adminId, "agents.schedule");
        if (raw == null || raw.isBlank()) return true;
        try {
            JsonNode node = mapper.readTree(raw);
            if (!"FIXED".equals(node.path("type").asText("FREE"))) return true;
            return node.path("days").has(DOW_KEY.getOrDefault(day, ""));
        } catch (Exception e) {
            log.warn("Error al parsear agents.schedule adminId={}: {}", adminId, e.getMessage());
            return true;
        }
    }

    /**
     * Retorna la ventana de horario para un día específico.
     */
    public ScheduleWindow getWindowForDay(Long adminId, LocalDate date) {
        String raw = configUseCases.getValue(adminId, "agents.schedule");
        if (raw == null || raw.isBlank()) return new ScheduleWindow("FREE", true, null, null);
        try {
            JsonNode node = mapper.readTree(raw);
            String type = node.path("type").asText("FREE");
            return switch (type) {
                case "FIXED" -> getWindowForFixed(node, date);
                case "HOURS_PER_DAY" -> {
                    LocalTime start = LocalTime.parse(node.path("windowStart").asText("00:00"));
                    LocalTime end   = LocalTime.parse(node.path("windowEnd").asText("23:59"));
                    yield new ScheduleWindow("HOURS_PER_DAY", true, start, end);
                }
                default -> new ScheduleWindow("FREE", true, null, null);
            };
        } catch (Exception e) {
            log.warn("Error al parsear agents.schedule adminId={}: {}", adminId, e.getMessage());
            return new ScheduleWindow("FREE", true, null, null);
        }
    }

    private ScheduleWindow getWindowForFixed(JsonNode node, LocalDate date) {
        String dayKey = DOW_KEY.getOrDefault(date.getDayOfWeek(), "");
        JsonNode days = node.path("days");
        if (!days.has(dayKey)) return new ScheduleWindow("FIXED", false, null, null);
        JsonNode day = days.get(dayKey);
        LocalTime start = LocalTime.parse(day.path("start").asText("00:00"));
        LocalTime end   = LocalTime.parse(day.path("end").asText("23:59"));
        return new ScheduleWindow("FIXED", true, start, end);
    }

    // ── helpers privados ──────────────────────────────────────────────────────

    private boolean isWithinFixed(JsonNode node, OffsetDateTime now) {
        String dayKey = DOW_KEY.getOrDefault(now.getDayOfWeek(), "");
        JsonNode days = node.path("days");
        if (!days.has(dayKey)) return false;
        JsonNode day = days.get(dayKey);
        return isBetween(
                LocalTime.parse(day.path("start").asText("00:00")),
                LocalTime.parse(day.path("end").asText("23:59")),
                now.toLocalTime());
    }

    private boolean isWithinWindow(JsonNode node, LocalTime now) {
        return isBetween(
                LocalTime.parse(node.path("windowStart").asText("00:00")),
                LocalTime.parse(node.path("windowEnd").asText("23:59")),
                now);
    }

    private boolean isBetween(LocalTime start, LocalTime end, LocalTime now) {
        return !now.isBefore(start) && !now.isAfter(end);
    }
}
