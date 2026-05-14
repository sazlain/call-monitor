package com.monitor.call.domain.responses;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class ScheduleAdherenceRow {
    private String date;          // "2026-05-13"
    private Long   agentId;
    private String agentName;
    private String extension;
    private String scheduleType;  // FREE, FIXED, HOURS_PER_DAY
    private String expectedStart; // "09:00" or null
    private String expectedEnd;   // "18:00" or null
    private String firstCallAt;   // "09:15" or null
    private String lastCallAt;    // "17:45" or null
    private long   callCount;
    private String status;        // COMPLIANT, LATE, EARLY_LEAVE, ABSENT, DAY_OFF, FREE, OUT_OF_WINDOW
}
