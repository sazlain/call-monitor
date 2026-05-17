package com.monitor.call.infrastructure.exceptions;

import com.monitor.call.domain.exceptions.BusinessRuleException;
import com.monitor.call.domain.exceptions.ConflictException;
import com.monitor.call.domain.exceptions.ForbiddenException;
import com.monitor.call.domain.exceptions.NotFoundException;
import com.monitor.call.domain.exceptions.UnauthorizedException;
import com.monitor.call.exceptions.CallMonitorException;
import com.monitor.call.exceptions.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Errores de negocio definidos (CallMonitorException) ──────────────────

    @ExceptionHandler(CallMonitorException.class)
    public ResponseEntity<ErrorResponse> handleCallMonitorException(CallMonitorException ex) {
        logger.warn("Error de negocio: {}", ex.getMessage());
        return ResponseEntity
                .status(ex.getError().getHttpStatus())
                .body(ex.getError());
    }

    // ── Errores de validacion (@Valid) ────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Valor invalido",
                        (a, b) -> a
                ));

        Map<String, Object> body = new HashMap<>();
        body.put("error", "VALIDATION_ERROR");
        body.put("message", "Uno o mas campos tienen valores invalidos");
        body.put("fields", fieldErrors);

        logger.warn("Error de validacion: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ── Acceso denegado (roles insuficientes) ─────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        logger.warn("Acceso denegado: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setId("ERR403");
        error.setTitle("Acceso denegado");
        error.setMessage("No tienes permisos para realizar esta accion");
        error.setHttpStatus(HttpStatus.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ── Credenciales invalidas ────────────────────────────────────────────────

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        logger.warn("Credenciales invalidas");
        ErrorResponse error = new ErrorResponse();
        error.setId("ERR401");
        error.setTitle("No autorizado");
        error.setMessage("Email o contrasena incorrectos");
        error.setHttpStatus(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ── Header requerido faltante ─────────────────────────────────────────────

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        logger.warn("Header faltante: {}", ex.getHeaderName());
        ErrorResponse error = new ErrorResponse();
        error.setId("ERR400");
        error.setTitle("Header requerido faltante");
        error.setMessage("El header '" + ex.getHeaderName() + "' es requerido");
        error.setHttpStatus(HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ── Archivo CSV muy grande ────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        logger.warn("Archivo demasiado grande: {}", ex.getMessage());
        ErrorResponse error = new ErrorResponse();
        error.setId("ERR413");
        error.setTitle("Archivo demasiado grande");
        error.setMessage("El archivo supera el tamano maximo permitido (50MB)");
        error.setHttpStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    // ── Excepciones de dominio tipadas ────────────────────────────────────────

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        logger.warn("Recurso no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorOf("ERR404", "Recurso no encontrado", ex.getMessage(), HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        logger.warn("Conflicto: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorOf("ERR409", "Conflicto", ex.getMessage(), HttpStatus.CONFLICT));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        logger.warn("No autorizado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorOf("ERR401", "No autorizado", ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex) {
        String message = resolveAccountMessage(ex.getMessage());
        logger.warn("Acceso denegado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorOf("ERR403", "Acceso denegado", message, HttpStatus.FORBIDDEN));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        logger.warn("Regla de negocio: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorOf("ERR422", "Regla de negocio no cumplida", ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ── Entidad no encontrada (RuntimeException con mensaje especifico) ────────

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Error interno";

        // Detectar errores de "no encontrado" por mensaje
        if (message.toLowerCase().contains("no encontrado") ||
            message.toLowerCase().contains("not found")) {
            logger.warn("Recurso no encontrado: {}", message);
            ErrorResponse error = new ErrorResponse();
            error.setId("ERR404");
            error.setTitle("Recurso no encontrado");
            error.setMessage(message);
            error.setHttpStatus(HttpStatus.NOT_FOUND);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        // Cuenta desactivada
        if (message.equals("ACCOUNT_DISABLED")) {
            logger.warn("Acceso bloqueado por cuenta desactivada");
            ErrorResponse error = new ErrorResponse();
            error.setId("ERR403");
            error.setTitle("Cuenta desactivada");
            error.setMessage("Tu cuenta ha sido desactivada. Contacta al administrador de la plataforma.");
            error.setHttpStatus(HttpStatus.FORBIDDEN);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // Errores de licencia
        if (message.equals("LICENSE_PENDING")) {
            logger.warn("Acceso bloqueado por licencia pendiente");
            ErrorResponse error = new ErrorResponse();
            error.setId("ERR403");
            error.setTitle("Licencia pendiente de activación");
            error.setMessage("Tu licencia aún no ha sido activada. Contacta al administrador de la plataforma.");
            error.setHttpStatus(HttpStatus.FORBIDDEN);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        if (message.equals("LICENSE_EXPIRED")) {
            logger.warn("Acceso bloqueado por licencia vencida");
            ErrorResponse error = new ErrorResponse();
            error.setId("ERR403");
            error.setTitle("Licencia vencida");
            error.setMessage("Tu licencia ha vencido. Contacta al administrador de la plataforma para renovarla.");
            error.setHttpStatus(HttpStatus.FORBIDDEN);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        if (message.equals("LICENSE_SUSPENDED")) {
            logger.warn("Acceso bloqueado por licencia suspendida");
            ErrorResponse error = new ErrorResponse();
            error.setId("ERR403");
            error.setTitle("Licencia suspendida");
            error.setMessage("Tu licencia está suspendida. Contacta al administrador de la plataforma.");
            error.setHttpStatus(HttpStatus.FORBIDDEN);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }
        if (message.equals("LICENSE_TRIAL")) {
            logger.warn("Acceso bloqueado por licencia en período de prueba sin activar");
            ErrorResponse error = new ErrorResponse();
            error.setId("ERR403");
            error.setTitle("Licencia en período de prueba");
            error.setMessage("La licencia de tu organización está en período de prueba. Contacta al administrador de la plataforma.");
            error.setHttpStatus(HttpStatus.FORBIDDEN);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // Detectar conflictos (duplicados)
        if (message.toLowerCase().contains("ya existe") ||
            message.toLowerCase().contains("ya esta registrad") ||
            message.toLowerCase().contains("ya está registrad") ||
            message.toLowerCase().contains("ya está en uso") ||
            message.toLowerCase().contains("ya fue tipificada")) {
            logger.warn("Conflicto: {}", message);
            ErrorResponse error = new ErrorResponse();
            error.setId("ERR409");
            error.setTitle("Conflicto");
            error.setMessage(message);
            error.setHttpStatus(HttpStatus.CONFLICT);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // Error generico
        logger.error("Error no controlado: {}", message, ex);
        ErrorResponse error = new ErrorResponse();
        error.setId("ERR500");
        error.setTitle("Error interno del servidor");
        error.setMessage("Ocurrio un error inesperado. Por favor intenta nuevamente.");
        error.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ── Cualquier otra excepcion ──────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        logger.error("Error no controlado: {}", ex.getMessage(), ex);
        ErrorResponse error = new ErrorResponse();
        error.setId("ERR500");
        error.setTitle("Error interno del servidor");
        error.setMessage("Ocurrio un error inesperado. Por favor intenta nuevamente.");
        error.setHttpStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ErrorResponse errorOf(String id, String title, String message, HttpStatus status) {
        ErrorResponse error = new ErrorResponse();
        error.setId(id);
        error.setTitle(title);
        error.setMessage(message);
        error.setHttpStatus(status);
        return error;
    }

    private String resolveAccountMessage(String code) {
        return switch (code) {
            case "ACCOUNT_DISABLED" ->
                "Tu cuenta ha sido desactivada. Contacta al administrador de la plataforma.";
            case "LICENSE_PENDING" ->
                "Tu licencia aún no ha sido activada. Contacta al administrador de la plataforma.";
            case "LICENSE_EXPIRED" ->
                "Tu licencia ha vencido. Contacta al administrador de la plataforma para renovarla.";
            case "LICENSE_SUSPENDED" ->
                "Tu licencia está suspendida. Contacta al administrador de la plataforma.";
            default -> code;
        };
    }
}
