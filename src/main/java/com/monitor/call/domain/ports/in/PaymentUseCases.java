package com.monitor.call.domain.ports.in;

import com.monitor.call.domain.enums.PaymentStatus;
import com.monitor.call.domain.responses.MyLicenseResponse;
import com.monitor.call.domain.responses.PaymentMethodResponse;
import com.monitor.call.domain.responses.PaymentSubmissionResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentUseCases {

    // ── Métodos de pago ────────────────────────────────────────────────────────

    /** Métodos activos (para selección en el formulario de pago del ADMIN) */
    List<PaymentMethodResponse> listPaymentMethods();

    /** Todos los métodos (activos e inactivos) — para gestión por SUPER_ADMIN */
    List<PaymentMethodResponse> listAllPaymentMethods();

    PaymentMethodResponse createPaymentMethod(String name, String details);

    PaymentMethodResponse updatePaymentMethod(Long id, String name, String details, boolean active);

    // ── Licencia del admin ─────────────────────────────────────────────────────

    MyLicenseResponse getMyLicense(Long adminId);

    // ── Comprobantes ───────────────────────────────────────────────────────────

    PaymentSubmissionResponse submitPayment(Long adminId, Long licenseId, Long paymentMethodId,
                                            BigDecimal amount, String notes, MultipartFile file);

    List<PaymentSubmissionResponse> listMySubmissions(Long adminId);

    // ── SUPER_ADMIN ────────────────────────────────────────────────────────────

    List<PaymentSubmissionResponse> listAllSubmissions(PaymentStatus statusFilter);

    PaymentSubmissionResponse approve(Long submissionId, Long reviewerId, String notes);

    PaymentSubmissionResponse reject(Long submissionId, Long reviewerId, String notes);

    Resource getFile(Long submissionId);
}
