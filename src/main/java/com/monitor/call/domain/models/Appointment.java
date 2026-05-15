package com.monitor.call.domain.models;

import com.monitor.call.domain.enums.AppointmentStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {
    private Long id;
    private Long leadId;
    private Long agentId;
    private String callId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private String address;
    private Integer attendees;
    private String notes;
    private AppointmentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
