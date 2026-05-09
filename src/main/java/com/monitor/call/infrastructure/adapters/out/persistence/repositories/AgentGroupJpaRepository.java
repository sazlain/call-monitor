package com.monitor.call.infrastructure.adapters.out.persistence.repositories;

import com.monitor.call.infrastructure.adapters.out.persistence.entities.AgentGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentGroupJpaRepository extends JpaRepository<AgentGroupEntity, Long> {
    List<AgentGroupEntity> findByAdminIdAndActiveTrue(Long adminId);
    List<AgentGroupEntity> findByActiveTrue();
    Optional<AgentGroupEntity> findByIdAndAdminId(Long id, Long adminId);
    boolean existsByNameAndAdminId(String name, Long adminId);
}
