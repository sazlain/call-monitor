package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.AppointmentResponse;
import com.monitor.call.infrastructure.requests.AppointmentRequest;

import java.util.List;

public interface AppointmentUseCases {

    /** Crear cita desde tipificación */
    AppointmentResponse create(AppointmentRequest request, Long agentId);

    /** Reagendar cita existente */
    AppointmentResponse reschedule(Long appointmentId, AppointmentRequest request);

    /** Cancelar cita — lead vuelve a INTERESTED */
    AppointmentResponse cancel(Long appointmentId, String reason);

    /** Confirmar cita — lead pasa a CONVERTED */
    AppointmentResponse confirm(Long appointmentId);

    /** Citas del agente autenticado */
    List<AppointmentResponse> listMyAppointments(Long agentId);

    /** Todas las citas — para admin */
    List<AppointmentResponse> listAll(Long adminId);

    /** Citas de un lead específico */
    List<AppointmentResponse> listByLead(Long leadId);

    AppointmentResponse attend(Long appointmentId);
}
