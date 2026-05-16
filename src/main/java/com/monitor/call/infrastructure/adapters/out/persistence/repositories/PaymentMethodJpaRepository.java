package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.PaymentMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, Long> {
    List<PaymentMethodEntity> findByActiveTrue();
    List<PaymentMethodEntity> findAllByOrderByCreatedAtAsc();
}
