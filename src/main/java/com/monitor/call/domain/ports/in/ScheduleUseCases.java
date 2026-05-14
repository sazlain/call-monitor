package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.ScheduleWindow;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface ScheduleUseCases {
    boolean isWithinSchedule(Long adminId, OffsetDateTime now);
    boolean isWorkDay(Long adminId, DayOfWeek day);
    ScheduleWindow getWindowForDay(Long adminId, LocalDate date);
}
