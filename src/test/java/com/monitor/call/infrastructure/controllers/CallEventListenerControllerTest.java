package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.enums.CallFlow;
import com.monitor.call.domain.enums.CallStatus;
import com.monitor.call.domain.models.CallEvent;
import com.monitor.call.domain.ports.in.CallEventListenerUseCases;
import com.monitor.call.domain.responses.CallEventListenerResponse;
import com.monitor.call.infrastructure.adapters.in.controllers.CallEventListenerController;
import com.monitor.call.infrastructure.security.JwtUtil;
import com.monitor.call.infrastructure.websocket.CallEventWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del flujo completo:
 *   POST /api/events/calls/event
 *     → normalización de extensión
 *     → guardado en BD (via use case)
 *     → emisión por WebSocket (via handler)
 *
 * Cubre los casos críticos:
 *  1. Extensión cruda de Net2Phone (2007102001) se normaliza a (2001) ANTES de guardar
 *  2. Extensión ya registrada no se modifica
 *  3. Payload por query params, por form-body y mixto
 *  4. Todos los CallStatus relevantes
 *  5. El WebSocket handler siempre recibe el evento con la extensión normalizada
 *  6. Errores de validación (payload vacío, status inválido)
 */
@WebMvcTest(
        controllers = CallEventListenerController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class CallEventListenerControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private CallEventListenerUseCases useCases;
    @MockitoBean private CallEventWebSocketHandler wsHandler;
    @MockitoBean private JwtUtil jwtUtil;

    // ── helpers ─────────────────────────────────────────────────────────────────

    /** Payload mínimo válido como query-string o form body. */
    private static String minimalPayload(String callId, String ext, String status, String flow) {
        return "CallID=" + callId
                + "&CallerExtension=" + ext
                + "&CallStatus=" + status
                + "&CallFlow=" + flow
                + "&CallerIDNum=6019168431"
                + "&CalledNumber=3157665297";
    }

    private CallEventListenerResponse responseFor(String callId, String status) {
        return CallEventListenerResponse.builder()
                .callId(callId)
                .status(status)
                .build();
    }

    @BeforeEach
    void defaultMocks() {
        // Por defecto la extensión ya está normalizada (sin prefijo Net2Phone)
        when(wsHandler.normalizeExtension(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        when(useCases.onCallEvent(any()))
                .thenReturn(responseFor("CALL-001", "CALLING"));

        doNothing().when(wsHandler).emit(any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. NORMALIZACIÓN DE EXTENSIÓN — el bug principal que se corrigió
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class ExtensionNormalization {

        @Test
        void rawNet2PhoneExtension_isNormalizedBeforeSavingAndEmitting() throws Exception {
            // Net2Phone envía "2007102001"; el agente está registrado como "2001"
            when(wsHandler.normalizeExtension("2007102001")).thenReturn("2001");
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-001", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-001", "2007102001", "CALLING", "out")))
                    .andExpect(status().isOk());

            // El use case recibe la extensión normalizada (se guarda "2001" en BD)
            ArgumentCaptor<CallEvent> savedCaptor = ArgumentCaptor.forClass(CallEvent.class);
            verify(useCases).onCallEvent(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getCallerExtension())
                    .as("La extensión guardada debe ser '2001', no '2007102001'")
                    .isEqualTo("2001");

            // El WebSocket handler también recibe la extensión normalizada
            ArgumentCaptor<CallEvent> emitCaptor = ArgumentCaptor.forClass(CallEvent.class);
            verify(wsHandler).emit(emitCaptor.capture());
            assertThat(emitCaptor.getValue().getCallerExtension())
                    .as("El WS debe emitir con extensión '2001'")
                    .isEqualTo("2001");
        }

        @Test
        void alreadyNormalizedExtension_isNotModified() throws Exception {
            // Extensión ya coincide con el registro del agente
            when(wsHandler.normalizeExtension("2001")).thenReturn("2001");
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-002", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-002", "2001", "CALLING", "out")))
                    .andExpect(status().isOk());

            ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
            verify(useCases).onCallEvent(captor.capture());
            assertThat(captor.getValue().getCallerExtension()).isEqualTo("2001");
        }

        @Test
        void normalizationAlwaysCalledBeforeUseCaseAndEmit() throws Exception {
            // Verifica el orden: normalizar → guardar → emitir
            when(wsHandler.normalizeExtension("2007102001")).thenReturn("2001");
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-003", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-003", "2007102001", "CALLING", "out")))
                    .andExpect(status().isOk());

            var inOrder = inOrder(wsHandler, useCases);
            inOrder.verify(wsHandler).normalizeExtension("2007102001");
            inOrder.verify(useCases).onCallEvent(any());
            inOrder.verify(wsHandler).emit(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. PARSEO DE PAYLOAD
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class PayloadParsing {

        @Test
        void queryParams_parsedCorrectly() throws Exception {
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-010", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .param("CallID", "C-010")
                            .param("CallerExtension", "1001")
                            .param("CallStatus", "CALLING")
                            .param("CallFlow", "out")
                            .param("CallerIDNum", "6019168431")
                            .param("CalledNumber", "3157665297"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.callId").value("C-010"))
                    .andExpect(jsonPath("$.status").value("CALLING"));

            ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
            verify(useCases).onCallEvent(captor.capture());
            assertThat(captor.getValue().getCallId()).isEqualTo("C-010");
            assertThat(captor.getValue().getCallStatus()).isEqualTo(CallStatus.CALLING);
            assertThat(captor.getValue().getCallFlow()).isEqualTo(CallFlow.out);
            assertThat(captor.getValue().getCallerIdNum()).isEqualTo("6019168431");
            assertThat(captor.getValue().getCalledNumber()).isEqualTo("3157665297");
        }

        @Test
        void formBody_parsedCorrectly() throws Exception {
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-011", "ANSWER"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-011", "1001", "ANSWER", "in")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.callId").value("C-011"))
                    .andExpect(jsonPath("$.status").value("ANSWER"));

            ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
            verify(useCases).onCallEvent(captor.capture());
            assertThat(captor.getValue().getCallStatus()).isEqualTo(CallStatus.ANSWER);
            assertThat(captor.getValue().getCallFlow()).isEqualTo(CallFlow.in);
        }

        @Test
        void mixedQueryAndBody_mergesPayload() throws Exception {
            // CallID y CallerExtension llegan en query params, el resto en body
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-012", "HANGUP"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("CallID", "C-012")
                            .param("CallerExtension", "1001")
                            .content("CallStatus=HANGUP&CallFlow=out&CallerIDNum=5551234&CalledNumber=9990000"))
                    .andExpect(status().isOk());

            ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
            verify(useCases).onCallEvent(captor.capture());
            assertThat(captor.getValue().getCallId()).isEqualTo("C-012");
            assertThat(captor.getValue().getCallerExtension()).isEqualTo("1001");
            assertThat(captor.getValue().getCallStatus()).isEqualTo(CallStatus.HANGUP);
        }

        @Test
        void allOptionalFields_parsedCorrectly() throws Exception {
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-013", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content("CallID=C-013"
                                    + "&CallerExtension=1001"
                                    + "&CallStatus=CALLING"
                                    + "&CallFlow=out"
                                    + "&CallerIDNum=5551001"
                                    + "&CallerIDName=John+Doe"
                                    + "&CalledNumber=5550001"
                                    + "&CalledDID=8005550001"
                                    + "&CalledExtension=2002"
                                    + "&CallAPIID=API-999"))
                    .andExpect(status().isOk());

            ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
            verify(useCases).onCallEvent(captor.capture());
            assertThat(captor.getValue().getCallerIdName()).isEqualTo("John Doe");
            assertThat(captor.getValue().getCalledDID()).isEqualTo("8005550001");
            assertThat(captor.getValue().getCalledExtension()).isEqualTo("2002");
            assertThat(captor.getValue().getCallAPIID()).isEqualTo("API-999");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. ESTADOS DE LLAMADA — todos deben guardarse y emitirse
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class CallStatuses {

        private void assertStatusFlowWorks(String callId, String status) throws Exception {
            when(useCases.onCallEvent(any())).thenReturn(responseFor(callId, status));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload(callId, "1001", status, "out")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.callId").value(callId))
                    .andExpect(jsonPath("$.status").value(status));

            // Cada estado se guarda en BD y se emite por WS
            verify(useCases, atLeastOnce()).onCallEvent(any());
            verify(wsHandler, atLeastOnce()).emit(any());

            reset(useCases, wsHandler);
            defaultMocks();
        }

        @Test void calling_savesAndEmits() throws Exception { assertStatusFlowWorks("C-20", "CALLING");  }
        @Test void answer_savesAndEmits()  throws Exception { assertStatusFlowWorks("C-21", "ANSWER");   }
        @Test void hangup_savesAndEmits()  throws Exception { assertStatusFlowWorks("C-22", "HANGUP");   }
        @Test void busy_savesAndEmits()    throws Exception { assertStatusFlowWorks("C-23", "BUSY");     }
        @Test void noanswer_savesAndEmits() throws Exception { assertStatusFlowWorks("C-24", "NOANSWER"); }
        @Test void cancel_savesAndEmits()  throws Exception { assertStatusFlowWorks("C-25", "CANCEL");   }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. EMISIÓN WEBSOCKET — verificaciones sobre el mensaje emitido
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class WebSocketEmission {

        @Test
        void emit_calledExactlyOncePerRequest() throws Exception {
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-30", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-30", "1001", "CALLING", "out")))
                    .andExpect(status().isOk());

            verify(wsHandler, times(1)).emit(any());
        }

        @Test
        void emittedEvent_containsCorrectCallIdAndStatus() throws Exception {
            when(wsHandler.normalizeExtension("1001")).thenReturn("1001");
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-31", "ANSWER"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-31", "1001", "ANSWER", "in")))
                    .andExpect(status().isOk());

            ArgumentCaptor<CallEvent> captor = ArgumentCaptor.forClass(CallEvent.class);
            verify(wsHandler).emit(captor.capture());
            assertThat(captor.getValue().getCallId()).isEqualTo("C-31");
            assertThat(captor.getValue().getCallStatus()).isEqualTo(CallStatus.ANSWER);
            assertThat(captor.getValue().getCallFlow()).isEqualTo(CallFlow.in);
        }

        @Test
        void emittedEvent_extensionMatchesSavedExtension() throws Exception {
            // Lo que se guarda en BD es lo mismo que se emite por WS
            when(wsHandler.normalizeExtension("2007102001")).thenReturn("2001");
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-32", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-32", "2007102001", "CALLING", "out")))
                    .andExpect(status().isOk());

            ArgumentCaptor<CallEvent> saveCaptor = ArgumentCaptor.forClass(CallEvent.class);
            ArgumentCaptor<CallEvent> emitCaptor = ArgumentCaptor.forClass(CallEvent.class);
            verify(useCases).onCallEvent(saveCaptor.capture());
            verify(wsHandler).emit(emitCaptor.capture());

            assertThat(saveCaptor.getValue().getCallerExtension())
                    .isEqualTo(emitCaptor.getValue().getCallerExtension())
                    .isEqualTo("2001");
        }

        @Test
        void wsEmit_calledAfterUseCaseSave() throws Exception {
            // El evento primero se persiste, luego se emite
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-33", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-33", "1001", "CALLING", "out")))
                    .andExpect(status().isOk());

            var inOrder = inOrder(useCases, wsHandler);
            inOrder.verify(useCases).onCallEvent(any());
            inOrder.verify(wsHandler).emit(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. RESPUESTA HTTP
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class HttpResponse {

        @Test
        void successfulEvent_returns200WithCallIdAndStatus() throws Exception {
            when(useCases.onCallEvent(any())).thenReturn(responseFor("C-40", "CALLING"));

            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content(minimalPayload("C-40", "1001", "CALLING", "out")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.callId").value("C-40"))
                    .andExpect(jsonPath("$.status").value("CALLING"));
        }

        @Test
        void emptyPayload_returns400() throws Exception {
            mvc.perform(post("/api/events/calls/event"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(useCases, wsHandler);
        }

        @Test
        void invalidCallStatus_returns400() throws Exception {
            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content("CallID=C-41&CallerExtension=1001"
                                    + "&CallStatus=UNKNOWN_STATUS"
                                    + "&CallFlow=out"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(useCases);
            verifyNoInteractions(wsHandler);
        }

        @Test
        void invalidCallFlow_returns400() throws Exception {
            mvc.perform(post("/api/events/calls/event")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .content("CallID=C-42&CallerExtension=1001"
                                    + "&CallStatus=CALLING"
                                    + "&CallFlow=sideways"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(useCases);
        }
    }
}
