package com.monitor.call.domain.ports.in;

import java.util.List;

public interface SystemConfigUseCases {

    /** Obtiene el valor efectivo de una clave (o default si no hay valor). */
    String getValue(Long adminId, String key);

    /** Obtiene el valor como boolean */
    boolean getBooleanValue(Long adminId, String key);

    /** Obtiene el valor como integer */
    int getIntValue(Long adminId, String key);
}
