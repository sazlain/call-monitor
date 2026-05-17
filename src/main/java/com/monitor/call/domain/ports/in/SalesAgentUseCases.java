package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.responses.SalesAgentResponse;
import java.util.List;

public interface SalesAgentUseCases {

    /** Crea un SALES_AGENT y lo asocia al admin que lo crea */
    SalesAgentResponse createSalesAgent(String name, String email,
                                        Long defaultCallAgentId, Long adminId);

    /** Lista todos los sales agents activos de un admin */
    List<SalesAgentResponse> listSalesAgents(Long adminId);

    /** Cambia el CALL_AGENT por defecto de un sales agent */
    SalesAgentResponse assignCallAgent(Long salesAgentId, Long callAgentId);

    /** Desactiva un sales agent */
    void deactivate(Long salesAgentId);
}
