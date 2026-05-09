package com.monitor.call.domain.responses;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import lombok.*;
import java.time.OffsetDateTime;

/**
 * Mensaje emitido por WebSocket a los clientes suscritos
 * cada vez que llega un evento del proveedor de telefonia.
 *
 * Canales:
 *   /topic/calls/agent/{extension}  -> el agente recibe sus propios eventos
 *   /topic/calls/group/{groupId}    -> el admin recibe eventos de su grupo
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CallEventWebSocketMessage {
    private String callId;
    private String callerExtension;
    private String callerIdNum;
    private String callerIdName;
    private String calledNumber;
    private String calledExtension;
    private CallStatus callStatus;
    private CallFlow callFlow;
    private String callAPIID;
    private OffsetDateTime timestamp;

    /**
     * Accion sugerida para el frontend segun el estado:
     *   CALLING  -> abrir formulario de captura de datos del contacto
     *   ANSWER   -> iniciar cronometro de llamada
     *   HANGUP   -> abrir formulario de tipificacion
     *   BUSY / NOANSWER / CANCEL -> cerrar formulario, marcar intento fallido
     */
    private String frontendAction;
}
