package com.monitor.call.domain.ports.in;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public interface ScheduleUseCases {
    boolean isWithinSchedule(Long adminId, OffsetDateTime now);
    boolean isWorkDay(Long adminId, DayOfWeek day);
    ScheduleWindow getWindowForDay(Long adminId, LocalDate date);

    record ScheduleWindow(
            String type,          // FREE, FIXED, HOURS_PER_DAY
            boolean isWorkDay,
            LocalTime windowStart,  // null for FREE
            LocalTime windowEnd     // null for FREE
    ) {}
}
