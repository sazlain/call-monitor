package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.CallEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CallEventJpaRepository extends JpaRepository<CallEventEntity, Long>, JpaSpecificationExecutor<CallEventEntity> {
}
