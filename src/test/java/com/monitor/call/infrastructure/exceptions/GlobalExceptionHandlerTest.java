package com.monitor.call.infrastructure.exceptions;

import com.monitor.call.domain.exceptions.BusinessRuleException;
import com.monitor.call.domain.exceptions.ConflictException;
import com.monitor.call.domain.exceptions.ForbiddenException;
import com.monitor.call.domain.exceptions.NotFoundException;
import com.monitor.call.domain.exceptions.UnauthorizedException;
import com.monitor.call.exceptions.CallMonitorException;
import com.monitor.call.exceptions.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ─── CallMonitorException ─────────────────────────────────────────────────

    @Test
    void handleCallMonitorException_returnsErrorResponseWithCorrectStatus() {
        ErrorResponse error = new ErrorResponse();
        error.setId("ERR001");
        error.setTitle("Test error");
        error.setMessage("Business error message");
        error.setHttpStatus(HttpStatus.UNPROCESSABLE_ENTITY);

        CallMonitorException ex = mock(CallMonitorException.class);
        when(ex.getError()).thenReturn(error);
        when(ex.getMessage()).thenReturn("Business error message");

        ResponseEntity<ErrorResponse> resp = handler.handleCallMonitorException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody()).isSameAs(error);
    }

    // ─── MethodArgumentNotValidException ─────────────────────────────────────

    @Test
    void handleValidationErrors_returnsBadRequestWithFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "email", "must not be blank"));
        bindingResult.addError(new FieldError("req", "name",  "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> resp = handler.handleValidationErrors(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsKey("fields");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) resp.getBody().get("fields");
        assertThat(fields).containsKey("email");
        assertThat(fields).containsKey("name");
        assertThat(resp.getBody().get("error")).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void handleValidationErrors_nullMessage_usesDefault() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "phone", null, false, null, null, null));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> resp = handler.handleValidationErrors(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) resp.getBody().get("fields");
        assertThat(fields.get("phone")).isEqualTo("Valor invalido");
    }

    // ─── AccessDeniedException ────────────────────────────────────────────────

    @Test
    void handleAccessDenied_returnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("no permission");

        ResponseEntity<ErrorResponse> resp = handler.handleAccessDenied(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getId()).isEqualTo("ERR403");
        assertThat(resp.getBody().getMessage()).contains("permisos");
    }

    // ─── BadCredentialsException ──────────────────────────────────────────────

    @Test
    void handleBadCredentials_returnsUnauthorized() {
        BadCredentialsException ex = new BadCredentialsException("bad creds");

        ResponseEntity<ErrorResponse> resp = handler.handleBadCredentials(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getId()).isEqualTo("ERR401");
        assertThat(resp.getBody().getMessage()).contains("contrasena");
    }

    // ─── MaxUploadSizeExceededException ──────────────────────────────────────

    @Test
    void handleMaxUploadSize_returnsPayloadTooLarge() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(50_000_000L);

        ResponseEntity<ErrorResponse> resp = handler.handleMaxUploadSize(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(resp.getBody().getId()).isEqualTo("ERR413");
        assertThat(resp.getBody().getMessage()).contains("50MB");
    }

    // ─── Typed domain exceptions ─────────────────────────────────────────────

    @Test
    void handleNotFound_returns404() {
        NotFoundException ex = new NotFoundException("Agente no encontrado: 99");

        ResponseEntity<ErrorResponse> resp = handler.handleNotFound(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getId()).isEqualTo("ERR404");
        assertThat(resp.getBody().getMessage()).contains("Agente no encontrado");
    }

    @Test
    void handleConflict_returns409() {
        ConflictException ex = new ConflictException("El email ya esta registrado");

        ResponseEntity<ErrorResponse> resp = handler.handleConflict(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getId()).isEqualTo("ERR409");
        assertThat(resp.getBody().getMessage()).contains("email");
    }

    @Test
    void handleUnauthorized_returns401() {
        UnauthorizedException ex = new UnauthorizedException("Credenciales invalidas");

        ResponseEntity<ErrorResponse> resp = handler.handleUnauthorized(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getId()).isEqualTo("ERR401");
        assertThat(resp.getBody().getMessage()).contains("Credenciales");
    }

    @Test
    void handleForbidden_accountDisabled_returnsHumanMessage() {
        ForbiddenException ex = new ForbiddenException("ACCOUNT_DISABLED");

        ResponseEntity<ErrorResponse> resp = handler.handleForbidden(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getMessage()).contains("desactivada");
    }

    @Test
    void handleForbidden_licensePending_returnsHumanMessage() {
        ForbiddenException ex = new ForbiddenException("LICENSE_PENDING");

        ResponseEntity<ErrorResponse> resp = handler.handleForbidden(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getMessage()).contains("activada");
    }

    @Test
    void handleForbidden_licenseExpired_returnsHumanMessage() {
        ForbiddenException ex = new ForbiddenException("LICENSE_EXPIRED");

        ResponseEntity<ErrorResponse> resp = handler.handleForbidden(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getMessage()).contains("vencido");
    }

    @Test
    void handleForbidden_licenseSuspended_returnsHumanMessage() {
        ForbiddenException ex = new ForbiddenException("LICENSE_SUSPENDED");

        ResponseEntity<ErrorResponse> resp = handler.handleForbidden(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getMessage()).contains("suspendida");
    }

    @Test
    void handleBusinessRule_returns422() {
        BusinessRuleException ex = new BusinessRuleException("AGENT_LIMIT_REACHED: límite de 5 agentes");

        ResponseEntity<ErrorResponse> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().getId()).isEqualTo("ERR422");
        assertThat(resp.getBody().getMessage()).contains("AGENT_LIMIT_REACHED");
    }

    // ─── RuntimeException ────────────────────────────────────────────────────

    @Test
    void handleRuntimeException_notFoundMessage_returns404() {
        RuntimeException ex = new RuntimeException("Lead no encontrado: 99");

        ResponseEntity<ErrorResponse> resp = handler.handleRuntimeException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getId()).isEqualTo("ERR404");
        assertThat(resp.getBody().getMessage()).contains("no encontrado");
    }

    @Test
    void handleRuntimeException_notFoundEnglish_returns404() {
        RuntimeException ex = new RuntimeException("Resource not found");

        ResponseEntity<ErrorResponse> resp = handler.handleRuntimeException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void handleRuntimeException_duplicateMessage_returns409() {
        RuntimeException ex = new RuntimeException("El email ya esta registrado");

        ResponseEntity<ErrorResponse> resp = handler.handleRuntimeException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getId()).isEqualTo("ERR409");
    }

    @Test
    void handleRuntimeException_typificationDuplicate_returns409() {
        RuntimeException ex = new RuntimeException("Esta llamada ya fue tipificada");

        ResponseEntity<ErrorResponse> resp = handler.handleRuntimeException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleRuntimeException_genericMessage_returns500() {
        RuntimeException ex = new RuntimeException("Unexpected internal failure");

        ResponseEntity<ErrorResponse> resp = handler.handleRuntimeException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getId()).isEqualTo("ERR500");
    }

    @Test
    void handleRuntimeException_nullMessage_returns500() {
        RuntimeException ex = new RuntimeException((String) null);

        ResponseEntity<ErrorResponse> resp = handler.handleRuntimeException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ─── Generic Exception ────────────────────────────────────────────────────

    @Test
    void handleGenericException_returns500() {
        Exception ex = new Exception("Some checked exception");

        ResponseEntity<ErrorResponse> resp = handler.handleGenericException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getId()).isEqualTo("ERR500");
    }
}
