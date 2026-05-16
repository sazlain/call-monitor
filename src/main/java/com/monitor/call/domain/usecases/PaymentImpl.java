package com.monitor.call.domain.usecases;

import com.monitor.call.domain.enums.LicenseStatus;
import com.monitor.call.domain.enums.PaymentStatus;
import com.monitor.call.domain.ports.in.PaymentUseCases;
import com.monitor.call.domain.responses.MyLicenseResponse;
import com.monitor.call.domain.responses.PaymentMethodResponse;
import com.monitor.call.domain.responses.PaymentSubmissionResponse;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.LicenseEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.PaymentMethodEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.PaymentSubmissionEntity;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicenseJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.LicensePlanJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.PaymentMethodJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.PaymentSubmissionJpaRepository;
import com.monitor.call.infrastructure.adapters.out.persistence.repositories.UserJpaRepository;
import com.monitor.call.infrastructure.services.EmailService;
import com.monitor.call.infrastructure.services.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PaymentImpl implements PaymentUseCases {

    private static final Logger logger = LoggerFactory.getLogger(PaymentImpl.class);

    private final PaymentMethodJpaRepository methodRepo;
    private final PaymentSubmissionJpaRepository submissionRepo;
    private final LicenseJpaRepository licenseRepo;
    private final LicensePlanJpaRepository planRepo;
    private final UserJpaRepository userRepo;
    private final FileStorageService fileStorage;
    private final EmailService emailService;

    public PaymentImpl(PaymentMethodJpaRepository methodRepo,
                       PaymentSubmissionJpaRepository submissionRepo,
                       LicenseJpaRepository licenseRepo,
                       LicensePlanJpaRepository planRepo,
                       UserJpaRepository userRepo,
                       FileStorageService fileStorage,
                       EmailService emailService) {
        this.methodRepo    = methodRepo;
        this.submissionRepo = submissionRepo;
        this.licenseRepo   = licenseRepo;
        this.planRepo      = planRepo;
        this.userRepo      = userRepo;
        this.fileStorage   = fileStorage;
        this.emailService  = emailService;
    }

    // ── Métodos de pago ────────────────────────────────────────────────────────

    @Override
    public List<PaymentMethodResponse> listPaymentMethods() {
        return methodRepo.findByActiveTrue().stream()
                .map(this::toMethodResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PaymentMethodResponse> listAllPaymentMethods() {
        return methodRepo.findAllByOrderByCreatedAtAsc().stream()
                .map(this::toMethodResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentMethodResponse createPaymentMethod(String name, String details) {
        PaymentMethodEntity entity = PaymentMethodEntity.builder()
                .name(name).details(details).active(true).build();
        return toMethodResponse(methodRepo.save(entity));
    }

    @Override
    @Transactional
    public PaymentMethodResponse updatePaymentMethod(Long id, String name, String details, boolean active) {
        PaymentMethodEntity entity = methodRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Método de pago no encontrado"));
        if (name != null)    entity.setName(name);
        if (details != null) entity.setDetails(details);
        entity.setActive(active);
        return toMethodResponse(methodRepo.save(entity));
    }

    // ── Licencia del admin ─────────────────────────────────────────────────────

    @Override
    public MyLicenseResponse getMyLicense(Long adminId) {
        LicenseEntity license = licenseRepo.findByAdminId(adminId)
                .orElseThrow(() -> new RuntimeException("Licencia no encontrada para el admin"));

        var plan = license.getPlanId() != null
                ? planRepo.findById(license.getPlanId()).orElse(null)
                : null;

        Integer durationDays = (plan != null) ? plan.getDurationDays() : 30;
        BigDecimal price = (plan != null) ? plan.getPrice() : license.getPriceMonthly();

        return MyLicenseResponse.builder()
                .licenseId(license.getId())
                .planName(license.getPlanName())
                .billingCycle(license.getBillingCycle())
                .durationDays(durationDays)
                .price(price)
                .currentStatus(license.getStatus())
                .expiresAt(license.getExpirationDate())
                .startDate(license.getStartDate())
                .build();
    }

    // ── Comprobantes ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentSubmissionResponse submitPayment(Long adminId, Long licenseId,
                                                    Long paymentMethodId, BigDecimal amount,
                                                    String notes, MultipartFile file) {
        // Guardar placeholder para obtener el ID
        PaymentSubmissionEntity submission = PaymentSubmissionEntity.builder()
                .adminId(adminId)
                .licenseId(licenseId)
                .paymentMethodId(paymentMethodId)
                .amount(amount)
                .adminNotes(notes)
                .status(PaymentStatus.PENDING)
                .build();
        submission = submissionRepo.save(submission);

        // Almacenar archivo usando el ID ya generado
        if (file != null && !file.isEmpty()) {
            String filePath = fileStorage.storePaymentFile(file, submission.getId());
            submission.setFilePath(filePath);
            submission.setOriginalFilename(file.getOriginalFilename());
            submission.setFileContentType(file.getContentType());
            submission = submissionRepo.save(submission);
        }

        logger.info("Comprobante enviado: adminId={} licenseId={} amount={} submissionId={}",
                adminId, licenseId, amount, submission.getId());

        return toSubmissionResponse(submission);
    }

    @Override
    public List<PaymentSubmissionResponse> listMySubmissions(Long adminId) {
        return submissionRepo.findByAdminIdOrderBySubmittedAtDesc(adminId).stream()
                .map(this::toSubmissionResponse)
                .collect(Collectors.toList());
    }

    // ── SUPER_ADMIN ────────────────────────────────────────────────────────────

    @Override
    public List<PaymentSubmissionResponse> listAllSubmissions(PaymentStatus statusFilter) {
        List<PaymentSubmissionEntity> submissions = (statusFilter != null)
                ? submissionRepo.findByStatusOrderBySubmittedAtDesc(statusFilter)
                : submissionRepo.findAllByOrderBySubmittedAtDesc();
        return submissions.stream().map(this::toSubmissionResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentSubmissionResponse approve(Long submissionId, Long reviewerId, String notes) {
        PaymentSubmissionEntity submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado"));

        submission.setStatus(PaymentStatus.APPROVED);
        submission.setReviewedBy(reviewerId);
        submission.setReviewerNotes(notes);
        submission.setReviewedAt(OffsetDateTime.now());
        submissionRepo.save(submission);

        // Activar la licencia
        activateLicense(submission.getLicenseId());

        // Notificar al admin por email
        notifyAdmin(submission.getAdminId(), true, notes);

        logger.info("Comprobante {} aprobado por reviewer {}", submissionId, reviewerId);
        return toSubmissionResponse(submission);
    }

    @Override
    @Transactional
    public PaymentSubmissionResponse reject(Long submissionId, Long reviewerId, String notes) {
        PaymentSubmissionEntity submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado"));

        submission.setStatus(PaymentStatus.REJECTED);
        submission.setReviewedBy(reviewerId);
        submission.setReviewerNotes(notes);
        submission.setReviewedAt(OffsetDateTime.now());
        submissionRepo.save(submission);

        // Notificar al admin por email
        notifyAdmin(submission.getAdminId(), false, notes);

        logger.info("Comprobante {} rechazado por reviewer {}", submissionId, reviewerId);
        return toSubmissionResponse(submission);
    }

    @Override
    public Resource getFile(Long submissionId) {
        PaymentSubmissionEntity submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado"));
        if (submission.getFilePath() == null) {
            throw new RuntimeException("Este comprobante no tiene archivo adjunto");
        }
        return fileStorage.loadFile(submission.getFilePath());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void activateLicense(Long licenseId) {
        LicenseEntity license = licenseRepo.findById(licenseId)
                .orElseThrow(() -> new RuntimeException("Licencia no encontrada: " + licenseId));

        var plan = license.getPlanId() != null
                ? planRepo.findById(license.getPlanId()).orElse(null)
                : null;

        OffsetDateTime now = OffsetDateTime.now();
        int durationDays = (plan != null) ? plan.getDurationDays() : 30;

        license.setStatus(LicenseStatus.ACTIVE);
        license.setActivatedAt(now);
        license.setStartDate(now);
        license.setExpirationDate(now.plusDays(durationDays));
        licenseRepo.save(license);

        logger.info("Licencia {} activada — vence en {} días ({})", licenseId, durationDays, license.getExpirationDate());
    }

    private void notifyAdmin(Long adminId, boolean approved, String reviewerNotes) {
        try {
            userRepo.findById(adminId).ifPresent(user -> {
                String subject = approved
                        ? "✅ Pago aprobado — Licencia activada"
                        : "❌ Comprobante de pago rechazado";
                String body = approved
                        ? "<h2>Tu pago ha sido aprobado</h2><p>Tu licencia ha sido activada exitosamente.</p>"
                          + (reviewerNotes != null && !reviewerNotes.isBlank()
                              ? "<p><strong>Nota:</strong> " + reviewerNotes + "</p>" : "")
                        : "<h2>Tu comprobante fue rechazado</h2>"
                          + (reviewerNotes != null && !reviewerNotes.isBlank()
                              ? "<p><strong>Motivo:</strong> " + reviewerNotes + "</p>" : "")
                          + "<p>Por favor sube un nuevo comprobante válido.</p>";
                emailService.send(user.getEmail(), subject, body);
            });
        } catch (Exception e) {
            logger.warn("No se pudo enviar email de notificación de pago: {}", e.getMessage());
        }
    }

    private PaymentMethodResponse toMethodResponse(PaymentMethodEntity e) {
        return PaymentMethodResponse.builder()
                .id(e.getId()).name(e.getName()).details(e.getDetails())
                .active(e.getActive()).createdAt(e.getCreatedAt())
                .build();
    }

    private PaymentSubmissionResponse toSubmissionResponse(PaymentSubmissionEntity e) {
        String adminName  = userRepo.findById(e.getAdminId()).map(u -> u.getName()).orElse("—");
        String adminEmail = userRepo.findById(e.getAdminId()).map(u -> u.getEmail()).orElse("—");
        String reviewerName = e.getReviewedBy() != null
                ? userRepo.findById(e.getReviewedBy()).map(u -> u.getName()).orElse("—")
                : null;
        PaymentMethodResponse method = methodRepo.findById(e.getPaymentMethodId())
                .map(this::toMethodResponse).orElse(null);

        return PaymentSubmissionResponse.builder()
                .id(e.getId())
                .adminId(e.getAdminId())
                .adminName(adminName)
                .adminEmail(adminEmail)
                .licenseId(e.getLicenseId())
                .paymentMethod(method)
                .amount(e.getAmount())
                .originalFilename(e.getOriginalFilename())
                .fileContentType(e.getFileContentType())
                .status(e.getStatus())
                .adminNotes(e.getAdminNotes())
                .reviewerNotes(e.getReviewerNotes())
                .submittedAt(e.getSubmittedAt())
                .reviewedAt(e.getReviewedAt())
                .reviewedByName(reviewerName)
                .build();
    }
}
