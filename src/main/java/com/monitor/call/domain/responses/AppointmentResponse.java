package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.AppointmentStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentResponse {
    private Long id;
    private Long leadId;
    private Long agentId;
    private String agentName;
    private String callId;

    // Datos del lead
    private String contactName;
    private String contactPhone;
    private String leadSource;

    // Datos de la cita
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private String address;
    private Integer attendees;
    private String notes;
    private AppointmentStatus status;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
