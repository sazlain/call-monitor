package com.monitor.call.infrastructure.controllers;

import com.monitor.call.domain.ports.in.CallHistoryUseCases;
import com.monitor.call.domain.responses.CallHistoryPage;
import com.monitor.call.infrastructure.adapters.in.controllers.CallEventController;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = CallEventController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class CallEventControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private CallHistoryUseCases historyUseCases;
    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserJpaRepository userJpaRepository;

    private static final String AUTH = "Bearer valid-token";

    private CallHistoryPage emptyPage(int page, int size) {
        return CallHistoryPage.builder()
                .content(List.of()).totalElements(0L).totalPages(0)
                .page(page).size(size).build();
    }

    // ─── GET /api/calls/history ──────────────────────────────────────────────

    @Test
    void getHistory_noFilters_returns200WithPage() throws Exception {
        when(historyUseCases.getCallHistory(isNull(), isNull(), any(), any(), eq(0), eq(25)))
                .thenReturn(emptyPage(0, 25));

        mvc.perform(get("/api/calls/history")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25));
    }

    @Test
    void getHistory_withExtension_passesExtension() throws Exception {
        when(historyUseCases.getCallHistory(eq("1001"), isNull(), any(), any(), eq(0), eq(25)))
                .thenReturn(emptyPage(0, 25));

        mvc.perform(get("/api/calls/history")
                .param("extension", "1001")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());

        verify(historyUseCases).getCallHistory(eq("1001"), isNull(), any(), any(), eq(0), eq(25));
    }

    @Test
    void getHistory_withStatusFilter_passesStatus() throws Exception {
        when(historyUseCases.getCallHistory(isNull(), eq("HANGUP"), any(), any(), eq(0), eq(25)))
                .thenReturn(emptyPage(0, 25));

        mvc.perform(get("/api/calls/history")
                .param("status", "HANGUP")
                .header("Authorization", AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void getHistory_customPagination_passesPageAndSize() throws Exception {
        when(historyUseCases.getCallHistory(isNull(), isNull(), any(), any(), eq(2), eq(10)))
                .thenReturn(emptyPage(2, 10));

        mvc.perform(get("/api/calls/history")
                .param("page", "2")
                .param("size", "10")
                .header("Authorization", AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10));
    }
}
