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

    /** Obtener cita por ID */
    AppointmentResponse findById(Long appointmentId);

    /** Marcar como reagendada sin crear nueva cita — lead pasa a APPOINTMENT_RESCHEDULED */
    AppointmentResponse markRescheduled(Long appointmentId);

    /** Reactivar cita reagendada — vuelve a SCHEDULED, lead pasa a APPOINTMENT */
    AppointmentResponse reactivate(Long appointmentId);

    /** Cancela la última cita activa del lead sin modificar el estado del lead.
     *  Usar cuando la tipificación ya actualiza el lead por su cuenta. */
    void cancelLatestByLeadId(Long leadId);
}
