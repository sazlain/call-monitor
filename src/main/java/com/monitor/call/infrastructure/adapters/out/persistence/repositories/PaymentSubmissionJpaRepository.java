package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.domain.enums.PaymentStatus;
import com.monitor.call.infrastructure.adapters.out.persistence.entities.PaymentSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentSubmissionJpaRepository extends JpaRepository<PaymentSubmissionEntity, Long> {
    List<PaymentSubmissionEntity> findByAdminIdOrderBySubmittedAtDesc(Long adminId);
    List<PaymentSubmissionEntity> findByStatusOrderBySubmittedAtDesc(PaymentStatus status);
    List<PaymentSubmissionEntity> findAllByOrderBySubmittedAtDesc();
}
