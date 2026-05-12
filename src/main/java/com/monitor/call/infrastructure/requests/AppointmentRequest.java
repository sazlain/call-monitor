package com.monitor.call.infrastructure.requests;

import com.monitor.call.infrastructure.config.LocalTimeDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentRequest {
    private String callId;
    private Long leadId;

    private LocalDate appointmentDate;

    @JsonDeserialize(using = LocalTimeDeserializer.class)
    private LocalTime appointmentTime;

    private String address;
    private Integer attendees;
    private String notes;

    // Para reagendar
    private Long rescheduleAppointmentId;
}
