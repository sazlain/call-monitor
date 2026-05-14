package com.monitor.call.infrastructure.services;

import com.monitor.call.domain.ports.in.ScheduleUseCases;
import com.monitor.call.domain.ports.in.SystemConfigUseCases;
import com.monitor.call.domain.responses.ScheduleWindow;
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

    @Override
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

    @Override
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

    @Override
    public ScheduleWindow getWindowForDay(Long adminId, LocalDate date) {
        String raw = configUseCases.getValue(adminId, "agents.schedule");
        if (raw == null || raw.isBlank()) return ScheduleWindow.free();
        try {
            JsonNode node = mapper.readTree(raw);
            String type = node.path("type").asText("FREE");
            return switch (type) {
                case "FIXED" -> {
                    String dayKey = DOW_KEY.getOrDefault(date.getDayOfWeek(), "");
                    JsonNode days = node.path("days");
                    if (!days.has(dayKey)) yield ScheduleWindow.dayOff();
                    JsonNode day = days.get(dayKey);
                    yield ScheduleWindow.fixed(
                            LocalTime.parse(day.path("start").asText("00:00")),
                            LocalTime.parse(day.path("end").asText("23:59")));
                }
                case "HOURS_PER_DAY" -> ScheduleWindow.hoursPerDay(
                        LocalTime.parse(node.path("windowStart").asText("00:00")),
                        LocalTime.parse(node.path("windowEnd").asText("23:59")));
                default -> ScheduleWindow.free();
            };
        } catch (Exception e) {
            log.warn("Error al parsear agents.schedule adminId={}: {}", adminId, e.getMessage());
            return ScheduleWindow.free();
        }
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
