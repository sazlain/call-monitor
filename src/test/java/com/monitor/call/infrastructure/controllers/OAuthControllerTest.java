package com.monitor.call.infrastructure.controllers;

import com.monitor.call.infrastructure.adapters.in.controllers.OAuthController;
import com.monitor.call.infrastructure.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = OAuthController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class OAuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private JwtUtil jwtUtil;

    @Test
    void handleCallback_shortState_returns400() throws Exception {
        mvc.perform(get("/oauth/callback")
                .param("code", "auth-code-123")
                .param("state", "short"))   // length < 16 → invalid
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid state"));
    }

    @Test
    void handleCallback_nullState_returns400() throws Exception {
        mvc.perform(get("/oauth/callback")
                .param("code", "auth-code-123")
                .param("state", ""))        // empty → invalid
                .andExpect(status().isBadRequest());
    }
}
