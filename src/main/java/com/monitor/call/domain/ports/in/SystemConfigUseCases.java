package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.SystemConfigResponse;
import java.util.List;

public interface SystemConfigUseCases {

    /** Lista todas las configuraciones de un admin */
    List<SystemConfigResponse> listByAdmin(Long adminId);

    /** Obtiene una configuración por clave */
    SystemConfigResponse getByKey(Long adminId, String key);

    /** Crea o actualiza el valor de una clave */
    SystemConfigResponse upsert(Long adminId, String key, String value);

    /** Siembra los valores por defecto para un admin si no existen */
    void seedDefaults(Long adminId);

    /** Obtiene el valor efectivo de una clave (o default si no hay valor). */
    String getValue(Long adminId, String key);

    /** Obtiene el valor como boolean */
    boolean getBooleanValue(Long adminId, String key);

    /** Obtiene el valor como integer */
    int getIntValue(Long adminId, String key);
}
