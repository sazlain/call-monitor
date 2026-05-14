package com.monitor.call.domain.responses;

import java.time.LocalTime;

public record ScheduleWindow(
        String type,
        boolean isWorkDay,
        LocalTime windowStart,
        LocalTime windowEnd
) {
    public static ScheduleWindow free()                              { return new ScheduleWindow("FREE", true, null, null); }
    public static ScheduleWindow dayOff()                           { return new ScheduleWindow("FIXED", false, null, null); }
    public static ScheduleWindow fixed(LocalTime s, LocalTime e)    { return new ScheduleWindow("FIXED", true, s, e); }
    public static ScheduleWindow hoursPerDay(LocalTime s, LocalTime e) { return new ScheduleWindow("HOURS_PER_DAY", true, s, e); }
}
