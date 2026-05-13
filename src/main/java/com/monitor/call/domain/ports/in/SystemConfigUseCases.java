package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.SystemConfigResponse;

import java.util.List;

public interface SystemConfigUseCases {

    /** Retorna todos los configs del admin */
    List<SystemConfigResponse> listByAdmin(Long adminId);

    /** Retorna un config por clave */
    SystemConfigResponse getByKey(Long adminId, String key);

    /** Actualiza el valor de una clave. La crea si no existe. */
    SystemConfigResponse upsert(Long adminId, String key, String value);

    /** Crea los configs por defecto si no existen (idempotente). */
    void seedDefaults(Long adminId);

    /** Obtiene el valor efectivo de una clave (o default si no hay valor). */
    String getValue(Long adminId, String key);

    /** Obtiene el valor como boolean */
    boolean getBooleanValue(Long adminId, String key);

    /** Obtiene el valor como integer */
    int getIntValue(Long adminId, String key);
}
