package com.monitor.call.domain.enums;

public enum AppointmentStatus {
    SCHEDULED,    // Agendada — esperando realizarse
    CONFIRMED,    // Confirmada — cliente confirmó asistencia
    CANCELLED,    // Cancelada — lead vuelve a INTERESTED
    RESCHEDULED,   // Reagendada — se creó una nueva cita
    ATTENDED,
}
