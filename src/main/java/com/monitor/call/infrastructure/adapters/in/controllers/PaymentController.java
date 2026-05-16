package com.monitor.call.infrastructure.adapters.in.controllers;

import com.monitor.call.domain.enums.PaymentStatus;
import com.monitor.call.domain.ports.in.PaymentUseCases;
import com.monitor.call.domain.responses.MyLicenseResponse;
import com.monitor.call.domain.responses.PaymentMethodResponse;
import com.monitor.call.domain.responses.PaymentSubmissionResponse;
import com.monitor.call.infrastructure.requests.CreatePaymentMethodRequest;
import com.monitor.call.infrastructure.requests.ReviewPaymentRequest;
import com.monitor.call.infrastructure.requests.UpdatePaymentMethodRequest;
import com.monitor.call.infrastructure.security.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@RestController
@Tag(name = "Payments", description = "Gestión de pagos y comprobantes")
public class PaymentController {

    private final PaymentUseCases paymentUseCases;
    private final JwtUtil jwtUtil;

    public PaymentController(PaymentUseCases paymentUseCases, JwtUtil jwtUtil) {
        this.paymentUseCases = paymentUseCases;
        this.jwtUtil = jwtUtil;
    }

    // ── ADMIN: Métodos de pago ─────────────────────────────────────────────────

    @GetMapping("/api/payments/methods")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar métodos de pago activos")
    public ResponseEntity<List<PaymentMethodResponse>> listPaymentMethods() {
        return ResponseEntity.ok(paymentUseCases.listPaymentMethods());
    }

    // ── ADMIN: Mi licencia ─────────────────────────────────────────────────────

    @GetMapping("/api/payments/my-license")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Obtener información de la licencia del admin autenticado")
    public ResponseEntity<MyLicenseResponse> getMyLicense(
            @RequestHeader("Authorization") String auth) {
        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(paymentUseCases.getMyLicense(adminId));
    }

    // ── ADMIN: Enviar comprobante ──────────────────────────────────────────────

    @PostMapping(value = "/api/payments/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar comprobante de pago")
    public ResponseEntity<PaymentSubmissionResponse> submitPayment(
            @RequestHeader("Authorization") String auth,
            @RequestParam Long licenseId,
            @RequestParam Long paymentMethodId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) MultipartFile file) {
        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(
                paymentUseCases.submitPayment(adminId, licenseId, paymentMethodId, amount, notes, file));
    }

    // ── ADMIN: Mis comprobantes ────────────────────────────────────────────────

    @GetMapping("/api/payments/my")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar mis comprobantes enviados")
    public ResponseEntity<List<PaymentSubmissionResponse>> listMySubmissions(
            @RequestHeader("Authorization") String auth) {
        Long adminId = jwtUtil.extractUserId(auth.substring(7));
        return ResponseEntity.ok(paymentUseCases.listMySubmissions(adminId));
    }

    // ── SUPER_ADMIN: Comprobantes ──────────────────────────────────────────────

    @GetMapping("/api/superadmin/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar todos los comprobantes (SUPER_ADMIN)")
    public ResponseEntity<List<PaymentSubmissionResponse>> listAllSubmissions(
            @RequestParam(required = false) PaymentStatus status) {
        return ResponseEntity.ok(paymentUseCases.listAllSubmissions(status));
    }

    @GetMapping("/api/superadmin/payments/{id}/file")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Descargar archivo adjunto de un comprobante")
    public ResponseEntity<Resource> getFile(@PathVariable Long id) {
        Resource resource = paymentUseCases.getFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/api/superadmin/payments/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Aprobar un comprobante de pago")
    public ResponseEntity<PaymentSubmissionResponse> approve(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth,
            @RequestBody(required = false) ReviewPaymentRequest request) {
        Long reviewerId = jwtUtil.extractUserId(auth.substring(7));
        String notes = request != null ? request.getNotes() : null;
        return ResponseEntity.ok(paymentUseCases.approve(id, reviewerId, notes));
    }

    @PostMapping("/api/superadmin/payments/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Rechazar un comprobante de pago")
    public ResponseEntity<PaymentSubmissionResponse> reject(
            @PathVariable Long id,
            @RequestHeader("Authorization") String auth,
            @RequestBody(required = false) ReviewPaymentRequest request) {
        Long reviewerId = jwtUtil.extractUserId(auth.substring(7));
        String notes = request != null ? request.getNotes() : null;
        return ResponseEntity.ok(paymentUseCases.reject(id, reviewerId, notes));
    }

    // ── SUPER_ADMIN: Métodos de pago ───────────────────────────────────────────

    @GetMapping("/api/superadmin/payment-methods")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar todos los métodos de pago (activos e inactivos)")
    public ResponseEntity<List<PaymentMethodResponse>> listAllPaymentMethods() {
        return ResponseEntity.ok(paymentUseCases.listAllPaymentMethods());
    }

    @PostMapping("/api/superadmin/payment-methods")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear un nuevo método de pago")
    public ResponseEntity<PaymentMethodResponse> createPaymentMethod(
            @Valid @RequestBody CreatePaymentMethodRequest request) {
        return ResponseEntity.ok(
                paymentUseCases.createPaymentMethod(request.getName(), request.getDetails()));
    }

    @PutMapping("/api/superadmin/payment-methods/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar un método de pago")
    public ResponseEntity<PaymentMethodResponse> updatePaymentMethod(
            @PathVariable Long id,
            @RequestBody UpdatePaymentMethodRequest request) {
        boolean active = request.getActive() != null ? request.getActive() : true;
        return ResponseEntity.ok(
                paymentUseCases.updatePaymentMethod(id, request.getName(), request.getDetails(), active));
    }
}
