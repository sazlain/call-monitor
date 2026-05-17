package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.enums.CallResult;
import com.monitor.call.domain.ports.in.CallTypificationUseCases;
import com.monitor.call.domain.responses.CallTypificationResponse;
import com.monitor.call.infrastructure.adapters.in.controllers.CallTypificationController;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = CallTypificationController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class CallTypificationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private CallTypificationUseCases typUseCases;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserJpaRepository userJpaRepository;

    private static final String AUTH = "Bearer valid-token";

    private CallTypificationResponse buildTyp(String callId, CallResult result) {
        return CallTypificationResponse.builder()
                .id(1L).callId(callId).result(result).agentName("Agent Ana").build();
    }

    // ─── POST /api/calls/typification ────────────────────────────────────────

    @Test
    void typify_validRequest_returns201() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(typUseCases.typify(any(), eq(10L)))
                .thenReturn(buildTyp("CALL-001", CallResult.SALE));

        mvc.perform(post("/api/calls/typification")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"callId":"CALL-001","result":"SALE","contactName":"Juan","contactPhone":"555"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.callId").value("CALL-001"))
                .andExpect(jsonPath("$.result").value("SALE"));
    }

    @Test
    void typify_missingCallId_returns400() throws Exception {
        mvc.perform(post("/api/calls/typification")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"result":"SALE"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void typify_missingResult_returns400() throws Exception {
        mvc.perform(post("/api/calls/typification")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"callId":"CALL-001"}
                        """))
                .andExpect(status().isBadRequest());
    }

    // ─── PUT /api/calls/{callId}/typification ────────────────────────────────

    @Test
    void update_validRequest_returns200() throws Exception {
        when(jwtUtil.extractUserId("valid-token")).thenReturn(10L);
        when(typUseCases.updateTypification(eq("CALL-001"), any(), eq(10L)))
                .thenReturn(buildTyp("CALL-001", CallResult.CALLBACK));

        mvc.perform(put("/api/calls/CALL-001/typification")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", AUTH)
                .content("""
                        {"callId":"CALL-001","result":"CALLBACK"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("CALLBACK"));
    }

    // ─── GET /api/calls/{callId}/typification ────────────────────────────────

    @Test
    void get_exists_returns200() throws Exception {
        when(typUseCases.getByCallId("CALL-001"))
                .thenReturn(buildTyp("CALL-001", CallResult.SALE));

        mvc.perform(get("/api/calls/CALL-001/typification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value("CALL-001"));
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(typUseCases.getByCallId("NO-CALL"))
                .thenThrow(new RuntimeException("Tipificacion no encontrado"));

        mvc.perform(get("/api/calls/NO-CALL/typification"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /api/calls/typifications/lead/{leadId} ──────────────────────────

    @Test
    void listByLead_returns200WithList() throws Exception {
        when(typUseCases.listByLead(5L))
                .thenReturn(List.of(buildTyp("CALL-001", CallResult.INTERESTED)));

        mvc.perform(get("/api/calls/typifications/lead/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].callId").value("CALL-001"));
    }
}
