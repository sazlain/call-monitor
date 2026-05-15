package com.monitor.call.infrastructure.adapters.in.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/oauth")
public class OAuthController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthController.class);

    @Value("${oauth.token-url}")
    private String tokenUrl;

    @Value("${oauth.client-id}")
    private String clientId;

    @Value("${oauth.client-secret}")
    private String clientSecret;

    @Value("${oauth.redirect-uri}")
    private String redirectUri;

    @GetMapping("/callback")
    public ResponseEntity<?> handleCallback(
            @RequestParam("code") String code,
            @RequestParam("state") String state) {

        // 1. Validar state (simplificado)
        if (!isValidState(state)) {
            return ResponseEntity.badRequest().body("Invalid state");
        }

        // 2. Intercambiar code por token
        String tokenResponse = requestAccessToken(code);

        return ResponseEntity.ok(tokenResponse);
    }

    private boolean isValidState(String state) {
        // Aquí deberías validar contra algo persistido (session, DB, cache)
        return state != null && state.length() >= 16;
    }

    private String requestAccessToken(String code) {
        try {
            RestTemplate restTemplate = new RestTemplate();

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("code", code);
            body.add("redirect_uri", redirectUri);
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(tokenUrl, request, String.class);

            logger.info("Token response: {}", response.getBody());

            return response.getBody();

        } catch (Exception e) {
            logger.error("Error obteniendo token OAuth: {}", e.getMessage(), e);
            throw new RuntimeException("Error getting token", e);
        }
    }
}

