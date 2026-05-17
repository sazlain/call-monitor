package com.monitor.call.infrastructure.controllers;

import com.monitor.call.infrastructure.adapters.in.controllers.DefaultController;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
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
    controllers = DefaultController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
class DefaultControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private UserJpaRepository userJpaRepository;

    @Test
    void hello_returns200WithHelloWorld() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, World!"));
    }
}
