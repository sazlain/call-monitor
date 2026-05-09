package com.monitor.call.domain.ports.in;

import java.time.OffsetDateTime;

public interface ReportUseCases {

    /**
     * Reporte detallado de llamadas de un agente.
     * Columnas: fecha, callId, numero, duracion, estado, tipificacion, resultado, notas
     */
    byte[] generateAgentCallReport(String extension, OffsetDateTime from, OffsetDateTime to);

    /**
     * Reporte comparativo de todos los agentes de un grupo o admin.
     * Columnas: agente, extension, grupo, total, contestadas, no_contestadas,
     *           tasa_contacto, duracion_total, duracion_promedio, ventas, conversion
     */
    byte[] generateGroupReport(Long adminId, Long groupId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Reporte detallado de leads de un agente de ventas.
     * Columnas: fecha, contacto, telefono, origen, estado, agente_asignado,
     *           fecha_llamada, resultado, notas, callback_fecha
     */
    byte[] generateLeadReport(Long ownerId, OffsetDateTime from, OffsetDateTime to);

    /**
     * Reporte de callbacks pendientes y vencidos.
     * Columnas: contacto, telefono, origen, agente_asignado, fecha_callback, estado_vencimiento
     */
    byte[] generateCallbackReport(Long userId, OffsetDateTime from, OffsetDateTime to);
}
